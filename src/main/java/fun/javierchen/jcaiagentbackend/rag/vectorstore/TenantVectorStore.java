package fun.javierchen.jcaiagentbackend.rag.vectorstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import org.postgresql.util.PGobject;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class TenantVectorStore implements VectorStore {

    private static final int MAX_EMBEDDING_INPUT_LENGTH = 2048;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String insertSql;

    public TenantVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, ObjectMapper objectMapper, String tableName) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate must not be null");
        Assert.notNull(embeddingModel, "embeddingModel must not be null");
        Assert.notNull(objectMapper, "objectMapper must not be null");
        Assert.hasText(tableName, "tableName must not be blank");
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.insertSql = "INSERT INTO " + tableName + " (id, tenant_id, content, metadata, embedding) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    public void add(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        List<float[]> embeddings = new ArrayList<>(documents.size());
        for (Document document : documents) {
            embeddings.add(embeddingModel.embed(document.getText()));
        }

        jdbcTemplate.batchUpdate(insertSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int index) throws SQLException {
                Document document = documents.get(index);
                float[] embedding = embeddings.get(index);
                Long tenantId = resolveTenantId(document.getMetadata());
                if (tenantId == null) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "租户未选择");
                }
                document.getMetadata().putIfAbsent("tenantId", tenantId.toString());

                ps.setObject(1, UUID.fromString(document.getId()));
                ps.setLong(2, tenantId);
                ps.setString(3, document.getText());
                ps.setObject(4, toJsonObject(document.getMetadata()));
                ps.setObject(5, toVectorObject(embedding));
            }

            @Override
            public int getBatchSize() {
                return documents.size();
            }
        });
    }

    @Override
    public void delete(List<String> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "租户未选择");
        }
        String sql = "DELETE FROM " + tableName + " WHERE tenant_id = ? AND id = ?";
        jdbcTemplate.batchUpdate(sql, idList, idList.size(), (ps, id) -> {
            ps.setLong(1, tenantId);
            ps.setObject(2, UUID.fromString(id));
        });
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        if (filterExpression == null) {
            return;
        }
        if (filterExpression.type() == Filter.ExpressionType.EQ) {
            Object left = filterExpression.left();
            Object right = filterExpression.right();
            if (left instanceof Filter.Key && right instanceof Filter.Value) {
                Filter.Key key = (Filter.Key) left;
                Filter.Value value = (Filter.Value) right;
                if ("documentId".equals(key.key())) {
                    Long tenantId = TenantContextHolder.getTenantId();
                    if (tenantId == null) {
                        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "租户未选择");
                    }
                    String sql = "DELETE FROM " + tableName + " WHERE tenant_id = ? AND metadata->>'documentId' = ?";
                    jdbcTemplate.update(sql, tenantId, String.valueOf(value.value()));
                    return;
                }
            }
        }
        throw new UnsupportedOperationException("Unsupported delete filter expression");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuery())) {
            return Collections.emptyList();
        }
        String embeddingQuery = normalizeEmbeddingInput(request.getQuery());
        if (!StringUtils.hasText(embeddingQuery)) {
            return Collections.emptyList();
        }
        Long tenantId = resolveTenantId(request.getFilterExpression());
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "租户未选择");
        }

        List<String> documentIdFilters = resolveFilterValues(request.getFilterExpression(), "documentId");
        float[] queryEmbedding = embeddingModel.embed(embeddingQuery);
        String documentFilterSql = "";
        if (!documentIdFilters.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(documentIdFilters.size(), "?"));
            documentFilterSql = "AND metadata->>'documentId' IN (" + placeholders + ")";
        }
        String sql = """
                SELECT id, content, metadata, (embedding <=> ?) AS distance
                FROM %s
                WHERE tenant_id = ?
                %s
                ORDER BY distance
                LIMIT ?
                """.formatted(tableName, documentFilterSql);

        List<Object> params = new ArrayList<>();
        params.add(toVectorObject(queryEmbedding));
        params.add(tenantId);
        if (!documentIdFilters.isEmpty()) {
            params.addAll(documentIdFilters);
        }
        params.add(request.getTopK());

        List<Document> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            Object metadataValue = rs.getObject("metadata");
            Map<String, Object> metadata = readMetadata(metadataValue);
            Double distance = rs.getObject("distance", Double.class);
            Double score = distance == null ? null : 1.0 - distance;
            return Document.builder()
                    .id(id)
                    .text(content)
                    .metadata(metadata)
                    .score(score)
                    .build();
        }, params.toArray());

        double threshold = request.getSimilarityThreshold();
        if (threshold <= 0) {
            return results;
        }
        List<Document> filtered = new ArrayList<>();
        for (Document document : results) {
            Double score = document.getScore();
            if (score != null && score >= threshold) {
                filtered.add(document);
            }
        }
        return filtered;
    }

    private String normalizeEmbeddingInput(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_EMBEDDING_INPUT_LENGTH) {
            return normalized;
        }
        String truncated = normalized.substring(0, MAX_EMBEDDING_INPUT_LENGTH).trim();
        log.warn("Embedding query truncated from {} to {} characters", normalized.length(), truncated.length());
        return truncated;
    }

    private Long resolveTenantId(Map<String, Object> metadata) {
        Object tenantIdValue = metadata.get("tenantId");
        if (tenantIdValue != null) {
            try {
                return Long.valueOf(tenantIdValue.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return TenantContextHolder.getTenantId();
    }

    private Long resolveTenantId(Filter.Expression filterExpression) {
        Long tenantId = resolveTenantIdFromExpression(filterExpression);
        if (tenantId != null) {
            return tenantId;
        }
        return TenantContextHolder.getTenantId();
    }

    private Long resolveTenantIdFromExpression(Filter.Expression filterExpression) {
        if (filterExpression == null) {
            return null;
        }
        if (filterExpression.type() == Filter.ExpressionType.EQ) {
            Object left = filterExpression.left();
            Object right = filterExpression.right();
            if (left instanceof Filter.Key && right instanceof Filter.Value) {
                Filter.Key key = (Filter.Key) left;
                Filter.Value value = (Filter.Value) right;
                if ("tenantId".equals(key.key()) && value.value() != null) {
                    try {
                        return Long.valueOf(String.valueOf(value.value()));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }

        // 尽量递归兼容 AND/OR/NOT 等组合表达式
        Object left = filterExpression.left();
        Object right = filterExpression.right();
        if (left instanceof Filter.Expression) {
            Long tenantId = resolveTenantIdFromExpression((Filter.Expression) left);
            if (tenantId != null) {
                return tenantId;
            }
        }
        if (right instanceof Filter.Expression) {
            Long tenantId = resolveTenantIdFromExpression((Filter.Expression) right);
            if (tenantId != null) {
                return tenantId;
            }
        }
        return null;
    }

    private List<String> resolveFilterValues(Filter.Expression filterExpression, String keyName) {
        List<String> values = new ArrayList<>();
        collectFilterValues(filterExpression, keyName, values);
        return values;
    }

    private void collectFilterValues(Filter.Expression filterExpression, String keyName, List<String> values) {
        if (filterExpression == null) {
            return;
        }
        if ((filterExpression.type() == Filter.ExpressionType.EQ || filterExpression.type() == Filter.ExpressionType.IN)
                && filterExpression.left() instanceof Filter.Key key
                && keyName.equals(key.key())) {
            Object right = filterExpression.right();
            if (right instanceof Filter.Value value && value.value() != null) {
                values.add(String.valueOf(value.value()));
                return;
            }
            if (right instanceof Object[] array) {
                for (Object item : array) {
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
                return;
            }
            if (right instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
                return;
            }
        }

        Object left = filterExpression.left();
        Object right = filterExpression.right();
        if (left instanceof Filter.Expression leftExpression) {
            collectFilterValues(leftExpression, keyName, values);
        }
        if (right instanceof Filter.Expression rightExpression) {
            collectFilterValues(rightExpression, keyName, values);
        }
    }

    private PGobject toJsonObject(Map<String, Object> metadata) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(objectMapper.writeValueAsString(metadata));
            return jsonObject;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metadata", e);
        }
    }

    private PGobject toVectorObject(float[] vector) {
        try {
            PGobject vectorObject = new PGobject();
            vectorObject.setType("vector");
            vectorObject.setValue(formatVector(vector));
            return vectorObject;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create vector parameter", e);
        }
    }

    private String formatVector(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private Map<String, Object> readMetadata(Object metadataValue) {
        if (metadataValue == null) {
            return Collections.emptyMap();
        }
        try {
            String json;
            if (metadataValue instanceof PGobject) {
                json = ((PGobject) metadataValue).getValue();
            } else {
                json = metadataValue.toString();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse metadata", e);
        }
    }
}
