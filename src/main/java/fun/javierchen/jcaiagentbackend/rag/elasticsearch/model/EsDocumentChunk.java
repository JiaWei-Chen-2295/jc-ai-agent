package fun.javierchen.jcaiagentbackend.rag.elasticsearch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder
public record EsDocumentChunk(
        String id,
        @JsonProperty("tenant_id") Long tenantId,
        @JsonProperty("document_id") String documentId,
        String content,
        Map<String, Object> metadata,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
) {
}
