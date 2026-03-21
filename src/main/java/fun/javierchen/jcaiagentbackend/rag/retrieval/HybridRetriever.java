package fun.javierchen.jcaiagentbackend.rag.retrieval;

import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.rag.elasticsearch.service.EsKeywordSearchService;
import fun.javierchen.jcaiagentbackend.rag.observability.RagRetrievalTrace;
import fun.javierchen.jcaiagentbackend.rag.observability.RagTraceStore;
import fun.javierchen.jcaiagentbackend.utils.VectorStoreFilterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStore studyFriendPGvectorStore;
    private final EsKeywordSearchService esKeywordSearchService;
    private final RagTraceStore ragTraceStore;

    @Value("${jc-ai-agent.rag.hybrid-search.enabled:true}")
    private boolean hybridSearchEnabled;

    @Value("${jc-ai-agent.rag.hybrid-search.rrf-k:60}")
    private int rrfK;

    public List<Document> search(String query, Long tenantId, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (tenantId != null) {
            builder.filterExpression(VectorStoreFilterUtils.buildTenantIdFilter(tenantId));
        }
        return search(builder.build());
    }

    public List<Document> search(SearchRequest request) {
        long totalStart = System.nanoTime();
        Long tenantId = resolveTenantId(request.getFilterExpression());
        RagRetrievalTrace trace = ragTraceStore.start(
                request.getQuery(),
                tenantId,
                request.getTopK(),
                hybridSearchEnabled,
                rrfK
        );

        if (!hybridSearchEnabled) {
            List<Document> vectorOnly = vectorSearch(request);
            long vectorLatencyMs = (System.nanoTime() - totalStart) / 1_000_000;
            ragTraceStore.recordVectorDocs(trace.getTraceId(), vectorOnly, vectorLatencyMs);
            ragTraceStore.markDegraded(trace.getTraceId(), "hybrid-search disabled");
            ragTraceStore.finish(trace.getTraceId(), vectorLatencyMs);
            return vectorOnly;
        }

        CompletableFuture<StageResult> vectorFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            List<Document> docs = vectorSearch(request);
            return new StageResult(docs, (System.nanoTime() - start) / 1_000_000, null);
        });
        CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try {
                List<Document> docs = esKeywordSearchService.search(request);
                ragTraceStore.recordEsDocs(trace.getTraceId(), docs, (System.nanoTime() - start) / 1_000_000);
                return docs;
            } catch (Exception e) {
                log.warn("ES 检索失败，降级为纯向量检索: {}", e.getMessage());
                ragTraceStore.markDegraded(trace.getTraceId(), e.getMessage());
                ragTraceStore.recordEsDocs(trace.getTraceId(), List.of(), (System.nanoTime() - start) / 1_000_000);
                return List.of();
            }
        });

        StageResult vectorResult = vectorFuture.join();
        List<Document> vectorResults = vectorResult.docs();
        ragTraceStore.recordVectorDocs(trace.getTraceId(), vectorResults, vectorResult.latencyMs());
        List<Document> keywordResults = keywordFuture.join();
        if (keywordResults.isEmpty()) {
            ragTraceStore.markDegraded(trace.getTraceId(), "es result empty or failed");
            ragTraceStore.finish(trace.getTraceId(), (System.nanoTime() - totalStart) / 1_000_000);
            return vectorResults;
        }
        if (vectorResults.isEmpty()) {
            ragTraceStore.recordMergedDocs(trace.getTraceId(), keywordResults, 0L);
            ragTraceStore.finish(trace.getTraceId(), (System.nanoTime() - totalStart) / 1_000_000);
            return keywordResults;
        }
        long mergeStart = System.nanoTime();
        List<Document> merged = RRFMerger.merge(List.of(vectorResults, keywordResults), request.getTopK(), rrfK);
        ragTraceStore.recordMergedDocs(trace.getTraceId(), merged, (System.nanoTime() - mergeStart) / 1_000_000);
        ragTraceStore.finish(trace.getTraceId(), (System.nanoTime() - totalStart) / 1_000_000);
        return merged;
    }

    public List<Document> vectorSearch(SearchRequest request) {
        Long tenantId = resolveTenantId(request.getFilterExpression());
        Long previousTenantId = TenantContextHolder.getTenantId();
        boolean shouldClear = false;
        if (previousTenantId == null && tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
            shouldClear = true;
        }
        try {
            return studyFriendPGvectorStore.similaritySearch(request);
        } finally {
            if (shouldClear) {
                TenantContextHolder.clear();
            }
        }
    }

    private Long resolveTenantId(Filter.Expression expression) {
        if (expression == null) {
            return TenantContextHolder.getTenantId();
        }
        if (expression.type() == Filter.ExpressionType.EQ
                && expression.left() instanceof Filter.Key key
                && expression.right() instanceof Filter.Value value
                && "tenantId".equals(key.key())
                && value.value() != null) {
            try {
                return Long.valueOf(String.valueOf(value.value()));
            } catch (NumberFormatException ignored) {
                return TenantContextHolder.getTenantId();
            }
        }
        Long fromLeft = expression.left() instanceof Filter.Expression left ? resolveTenantId(left) : null;
        if (fromLeft != null) {
            return fromLeft;
        }
        return expression.right() instanceof Filter.Expression right ? resolveTenantId(right) : TenantContextHolder.getTenantId();
    }

    private record StageResult(List<Document> docs, long latencyMs, String error) {
    }
}
