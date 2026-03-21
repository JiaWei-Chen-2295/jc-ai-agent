package fun.javierchen.jcaiagentbackend.rag.observability;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RagRetrievalTrace {

    private String traceId;

    private String query;

    private Long tenantId;

    private Integer topK;

    private boolean hybridEnabled;

    private Integer rrfK;

    private boolean degradedToVectorOnly;

    private String degradeReason;

    private Long vectorLatencyMs;

    private Long esLatencyMs;

    private Long mergeLatencyMs;

    private Long totalLatencyMs;

    private List<RagTraceDocView> vectorDocs;

    private List<RagTraceDocView> esDocs;

    private List<RagTraceDocView> mergedDocs;

    private LocalDateTime createdAt;
}
