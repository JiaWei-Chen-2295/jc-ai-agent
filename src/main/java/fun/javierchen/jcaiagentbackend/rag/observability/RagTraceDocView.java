package fun.javierchen.jcaiagentbackend.rag.observability;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagTraceDocView {

    private String id;

    private Double score;

    private String documentId;

    private String source;

    private String snippet;
}
