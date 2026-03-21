package fun.javierchen.jcaiagentbackend.rag.elasticsearch.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.rag.elasticsearch.service.EsKeywordSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PgToEsSyncService {

    private static final String VECTOR_TABLE = "study_friends";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EsKeywordSearchService esKeywordSearchService;

    public int fullSync() {
        String sql = """
                SELECT id::text AS id, tenant_id, content, metadata
                FROM %s
                ORDER BY tenant_id, id
                """.formatted(VECTOR_TABLE);
        List<PgChunkRecord> records = queryChunkRecords(sql);
        return syncRecords(records, false);
    }

    public int syncDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId不能为空");
        }
        String sql = """
                SELECT id::text AS id, tenant_id, content, metadata
                FROM %s
                WHERE metadata->>'documentId' = ?
                ORDER BY tenant_id, id
                """.formatted(VECTOR_TABLE);
        List<PgChunkRecord> records = queryChunkRecords(sql, documentId.toString());
        return syncRecords(records, true);
    }

    private List<PgChunkRecord> queryChunkRecords(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PgChunkRecord(
                rs.getString("id"),
                rs.getLong("tenant_id"),
                rs.getString("content"),
                rs.getObject("metadata")
        ), args);
    }

    private int syncRecords(List<PgChunkRecord> records, boolean clearBeforeSync) {
        if (records.isEmpty()) {
            return 0;
        }

        int synced = 0;
        List<Document> currentBatch = new ArrayList<>();
        Long currentTenantId = null;
        Long currentDocumentId = null;

        for (PgChunkRecord record : records) {
            Map<String, Object> metadata = readMetadata(record.metadata());
            Long tenantId = record.tenantId();
            Long documentId = parseLong(metadata.get("documentId"));
            if (documentId == null) {
                continue;
            }

            if (!currentBatch.isEmpty()
                    && (!tenantId.equals(currentTenantId) || !documentId.equals(currentDocumentId))) {
                if (clearBeforeSync) {
                    esKeywordSearchService.deleteByDocumentId(currentDocumentId, currentTenantId);
                }
                esKeywordSearchService.index(currentBatch, currentDocumentId, currentTenantId);
                synced += currentBatch.size();
                currentBatch = new ArrayList<>();
            }

            currentBatch.add(Document.builder()
                    .id(record.id())
                    .text(record.content())
                    .metadata(metadata)
                    .build());
            currentTenantId = tenantId;
            currentDocumentId = documentId;
        }

        if (!currentBatch.isEmpty() && currentTenantId != null && currentDocumentId != null) {
            if (clearBeforeSync) {
                esKeywordSearchService.deleteByDocumentId(currentDocumentId, currentTenantId);
            }
            esKeywordSearchService.index(currentBatch, currentDocumentId, currentTenantId);
            synced += currentBatch.size();
        }

        log.info("PG -> ES 文档同步完成: chunkCount={}, clearBeforeSync={}", synced, clearBeforeSync);
        return synced;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> readMetadata(Object metadataValue) {
        if (metadataValue == null) {
            return Map.of();
        }
        try {
            String json = metadataValue instanceof PGobject pgObject ? pgObject.getValue() : metadataValue.toString();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("读取 metadata 失败", e);
        }
    }

    private record PgChunkRecord(String id, Long tenantId, String content, Object metadata) {
    }
}
