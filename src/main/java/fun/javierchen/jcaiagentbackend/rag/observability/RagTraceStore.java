package fun.javierchen.jcaiagentbackend.rag.observability;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RagTraceStore {

    private final int maxTraces;

    private final Map<String, RagRetrievalTrace> traceMap = new ConcurrentHashMap<>();
    private final Deque<String> traceOrder = new LinkedList<>();
    private final Object orderLock = new Object();

    public RagTraceStore(@Value("${jc-ai-agent.rag.observability.max-traces:300}") int maxTraces) {
        this.maxTraces = Math.max(50, maxTraces);
    }

    public RagRetrievalTrace start(String query, Long tenantId, int topK, boolean hybridEnabled, int rrfK) {
        RagRetrievalTrace trace = RagRetrievalTrace.builder()
                .traceId(UUID.randomUUID().toString())
                .query(query)
                .tenantId(tenantId)
                .topK(topK)
                .hybridEnabled(hybridEnabled)
                .rrfK(rrfK)
                .degradedToVectorOnly(false)
                .createdAt(LocalDateTime.now())
                .vectorDocs(List.of())
                .esDocs(List.of())
                .mergedDocs(List.of())
                .build();
        traceMap.put(trace.getTraceId(), trace);
        synchronized (orderLock) {
            traceOrder.addFirst(trace.getTraceId());
            while (traceOrder.size() > maxTraces) {
                String removedId = traceOrder.removeLast();
                traceMap.remove(removedId);
            }
        }
        return trace;
    }

    public void recordVectorDocs(String traceId, List<Document> documents, long latencyMs) {
        RagRetrievalTrace trace = traceMap.get(traceId);
        if (trace == null) {
            return;
        }
        trace.setVectorLatencyMs(latencyMs);
        trace.setVectorDocs(toDocView(documents, "vector"));
    }

    public void recordEsDocs(String traceId, List<Document> documents, long latencyMs) {
        RagRetrievalTrace trace = traceMap.get(traceId);
        if (trace == null) {
            return;
        }
        trace.setEsLatencyMs(latencyMs);
        trace.setEsDocs(toDocView(documents, "es"));
    }

    public void recordMergedDocs(String traceId, List<Document> documents, long latencyMs) {
        RagRetrievalTrace trace = traceMap.get(traceId);
        if (trace == null) {
            return;
        }
        trace.setMergeLatencyMs(latencyMs);
        trace.setMergedDocs(toDocView(documents, "rrf"));
    }

    public void markDegraded(String traceId, String reason) {
        RagRetrievalTrace trace = traceMap.get(traceId);
        if (trace == null) {
            return;
        }
        trace.setDegradedToVectorOnly(true);
        trace.setDegradeReason(reason);
    }

    public void finish(String traceId, long totalLatencyMs) {
        RagRetrievalTrace trace = traceMap.get(traceId);
        if (trace == null) {
            return;
        }
        trace.setTotalLatencyMs(totalLatencyMs);
    }

    public RagRetrievalTrace get(String traceId) {
        return traceMap.get(traceId);
    }

    public List<RagRetrievalTrace> latest(int limit, Long tenantId) {
        int safeLimit = Math.max(1, Math.min(limit, maxTraces));
        List<RagRetrievalTrace> result = new ArrayList<>(safeLimit);
        synchronized (orderLock) {
            for (String traceId : traceOrder) {
                RagRetrievalTrace trace = traceMap.get(traceId);
                if (trace == null) {
                    continue;
                }
                if (tenantId != null && trace.getTenantId() != null && !tenantId.equals(trace.getTenantId())) {
                    continue;
                }
                result.add(trace);
                if (result.size() >= safeLimit) {
                    break;
                }
            }
        }
        return result;
    }

    private List<RagTraceDocView> toDocView(List<Document> docs, String source) {
        if (CollectionUtils.isEmpty(docs)) {
            return List.of();
        }
        List<RagTraceDocView> result = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();
            String documentId = metadata != null && metadata.get("documentId") != null
                    ? String.valueOf(metadata.get("documentId"))
                    : null;
            result.add(RagTraceDocView.builder()
                    .id(doc.getId())
                    .score(doc.getScore())
                    .documentId(documentId)
                    .source(source)
                    .snippet(truncate(doc.getText(), 180))
                    .build());
        }
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
