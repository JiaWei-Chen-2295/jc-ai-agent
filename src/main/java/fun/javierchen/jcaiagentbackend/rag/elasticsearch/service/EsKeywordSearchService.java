package fun.javierchen.jcaiagentbackend.rag.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.rag.elasticsearch.model.EsDocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class EsKeywordSearchService {

    private final ElasticsearchClient esClient;

    @Value("${jc-ai-agent.rag.hybrid-search.enabled:true}")
    private boolean hybridSearchEnabled;

    @Value("${jc-ai-agent.rag.hybrid-search.keyword-index:study_friends_bm25}")
    private String indexName;

    @Value("${jc-ai-agent.rag.hybrid-search.keyword-analyzer:ik_smart}")
    private String keywordAnalyzer;

    private final AtomicBoolean indexReady = new AtomicBoolean(false);
    private final Object indexInitLock = new Object();

    public List<Document> search(SearchRequest request) {
        if (!hybridSearchEnabled || request == null || !StringUtils.hasText(request.getQuery())) {
            return List.of();
        }

        Long tenantId = resolveTenantId(request.getFilterExpression());
        if (tenantId == null) {
            log.warn("ES 检索跳过，tenantId 缺失");
            return List.of();
        }
        List<String> documentIds = resolveDocumentIds(request.getFilterExpression());

        try {
            ensureIndexReady();
            SearchResponse<EsDocumentChunk> response = esClient.search(s -> s
                            .index(indexName)
                            .size(request.getTopK())
                            .query(q -> q.bool(b -> {
                                b.must(m -> m.match(mt -> mt.field("content").query(request.getQuery())));
                                b.filter(f -> f.term(t -> t.field("tenant_id").value(tenantId)));
                                if (documentIds.size() == 1) {
                                    b.filter(f -> f.term(t -> t.field("document_id").value(documentIds.getFirst())));
                                } else if (!documentIds.isEmpty()) {
                                    b.filter(f -> f.terms(t -> t.field("document_id")
                                            .terms(tf -> tf.value(documentIds.stream().map(FieldValue::of).toList()))));
                                }
                                return b;
                            })),
                    EsDocumentChunk.class);

            return response.hits().hits().stream()
                    .map(this::toDocument)
                    .toList();
        } catch (Exception e) {
            log.warn("ES 关键词检索失败，跳过: {}", e.getMessage());
            return List.of();
        }
    }

    public void index(List<Document> documents, Long documentId, Long tenantId) {
        if (!hybridSearchEnabled || CollectionUtils.isEmpty(documents) || tenantId == null || documentId == null) {
            return;
        }

        List<BulkOperation> operations = new ArrayList<>();
        String now = LocalDateTime.now().toString();
        for (Document document : documents) {
            if (document.getId() == null || !StringUtils.hasText(document.getText())) {
                continue;
            }
            Map<String, Object> metadata = sanitizeMetadata(document.getMetadata());
            metadata.putIfAbsent("tenantId", tenantId.toString());
            metadata.putIfAbsent("documentId", documentId.toString());

            EsDocumentChunk chunk = EsDocumentChunk.builder()
                    .id(document.getId())
                    .tenantId(tenantId)
                    .documentId(documentId.toString())
                    .content(document.getText())
                    .metadata(metadata)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            operations.add(BulkOperation.of(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(document.getId())
                    .document(chunk))));
        }

        if (operations.isEmpty()) {
            return;
        }

        try {
            ensureIndexReady();
            BulkRequest request = BulkRequest.of(b -> b.operations(operations).refresh(Refresh.False));
            esClient.bulk(request);
        } catch (Exception e) {
            throw new IllegalStateException("ES 写入失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> rawMetadata) {
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue(), 0));
        }
        return sanitized;
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > 5) {
            return String.valueOf(value);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof TemporalAccessor temporalAccessor) {
            return temporalAccessor.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    nested.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue(), depth + 1));
                }
            }
            return nested;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                list.add(sanitizeValue(item, depth + 1));
            }
            return list;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(sanitizeValue(java.lang.reflect.Array.get(value, i), depth + 1));
            }
            return list;
        }
        return String.valueOf(value);
    }

    public void deleteByDocumentId(Long documentId, Long tenantId) {
        if (!hybridSearchEnabled || documentId == null || tenantId == null) {
            return;
        }
        try {
            ensureIndexReady();
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("tenant_id").value(tenantId)))
                            .filter(f -> f.term(t -> t.field("document_id").value(documentId.toString()))))));
            esClient.deleteByQuery(request);
        } catch (Exception e) {
            throw new IllegalStateException("ES 删除失败: " + e.getMessage(), e);
        }
    }

    public void ensureIndexReady() throws IOException {
        if (!hybridSearchEnabled || indexReady.get()) {
            return;
        }
        synchronized (indexInitLock) {
            if (indexReady.get()) {
                return;
            }
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                createIndex();
                log.info("ES 索引不存在，已自动创建: index={}", indexName);
            }
            indexReady.set(true);
        }
    }

    private void createIndex() throws IOException {
        try {
            esClient.indices().create(c -> c
                    .index(indexName)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                            .analysis(a -> a.analyzer("hybrid_analyzer",
                                    analyzer -> analyzer.custom(custom -> custom.tokenizer(keywordAnalyzer)))))
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("tenant_id", p -> p.long_(l -> l))
                            .properties("document_id", p -> p.keyword(k -> k))
                            .properties("content", p -> p.text(t -> t
                                    .analyzer("hybrid_analyzer")
                                    .searchAnalyzer("hybrid_analyzer")))
                            .properties("metadata", p -> p.object(o -> o.enabled(true)))
                            .properties("created_at", p -> p.date(d -> d))
                            .properties("updated_at", p -> p.date(d -> d))));
        } catch (Exception ex) {
            log.warn("按 analyzer={} 创建索引失败，将回退到 standard analyzer: {}",
                    keywordAnalyzer, ex.getMessage());
            esClient.indices().create(c -> c
                    .index(indexName)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0"))
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("tenant_id", p -> p.long_(l -> l))
                            .properties("document_id", p -> p.keyword(k -> k))
                            .properties("content", p -> p.text(t -> t
                                    .analyzer("standard")
                                    .searchAnalyzer("standard")))
                            .properties("metadata", p -> p.object(o -> o.enabled(true)))
                            .properties("created_at", p -> p.date(d -> d))
                            .properties("updated_at", p -> p.date(d -> d))));
        }
    }

    private Document toDocument(Hit<EsDocumentChunk> hit) {
        EsDocumentChunk source = hit.source();
        Map<String, Object> metadata = source != null && source.metadata() != null ? source.metadata() : Map.of();
        return Document.builder()
                .id(hit.id())
                .text(source != null ? source.content() : "")
                .metadata(metadata)
                .score(hit.score() != null ? hit.score().doubleValue() : null)
                .build();
    }

    private Long resolveTenantId(Filter.Expression expression) {
        String value = resolveSingleValue(expression, "tenantId");
        if (!StringUtils.hasText(value)) {
            return TenantContextHolder.getTenantId();
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return TenantContextHolder.getTenantId();
        }
    }

    private List<String> resolveDocumentIds(Filter.Expression expression) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        collectFilterValues(expression, "documentId", results);
        return results.stream().filter(StringUtils::hasText).toList();
    }

    private String resolveSingleValue(Filter.Expression expression, String keyName) {
        List<String> values = new ArrayList<>();
        collectFilterValues(expression, keyName, values);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void collectFilterValues(Filter.Expression expression, String keyName, java.util.Collection<String> results) {
        if (expression == null) {
            return;
        }
        if (expression.left() instanceof Filter.Key key && keyName.equals(key.key())) {
            Object rightOperand = expression.right();
            if (rightOperand instanceof Filter.Value value && value.value() != null) {
                results.add(String.valueOf(value.value()));
            } else if (rightOperand instanceof Object[] values) {
                for (Object value : values) {
                    if (value != null) {
                        results.add(String.valueOf(value));
                    }
                }
            } else if (rightOperand instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value != null) {
                        results.add(String.valueOf(value));
                    }
                }
            }
        }
        if (expression.left() instanceof Filter.Expression left) {
            collectFilterValues(left, keyName, results);
        }
        if (expression.right() instanceof Filter.Expression right) {
            collectFilterValues(right, keyName, results);
        }
    }
}
