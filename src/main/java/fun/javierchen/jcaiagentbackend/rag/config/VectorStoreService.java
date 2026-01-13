package fun.javierchen.jcaiagentbackend.rag.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 向量存储服务
 * 提供向量的写入、查询、删除等操作
 *
 * @author JavierChen
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreService {

    private final VectorStore studyFriendPGvectorStore;
    private final JdbcTemplate jdbcTemplate;

    private static final String VECTOR_TABLE = "study_friends";

    /**
     * 添加文档到向量库
     *
     * @param documents  文档列表
     * @param documentId 关联的文档ID
     */
    public void addDocuments(List<Document> documents, Long documentId) {
        if (documents == null || documents.isEmpty()) {
            log.warn("文档列表为空，跳过向量写入: documentId={}", documentId);
            return;
        }

        // 为每个 Document 添加 documentId 到 metadata（用于后续按文档删除）
        documents.forEach(doc -> doc.getMetadata().put("documentId", documentId.toString()));

        studyFriendPGvectorStore.add(documents);
        log.info("向量写入成功: documentId={}, chunkCount={}", documentId, documents.size());
    }

    /**
     * 根据 documentId 删除所有关联的向量
     * 使用直接 SQL 删除，更高效
     *
     * @param documentId 文档ID
     * @return 删除的向量数量
     */
    @Transactional
    public int deleteByDocumentId(Long documentId) {
        String sql = """
                    DELETE FROM %s
                    WHERE metadata->>'documentId' = ?
                """.formatted(VECTOR_TABLE);

        int deleted = jdbcTemplate.update(sql, documentId.toString());
        log.info("删除向量成功: documentId={}, count={}", documentId, deleted);
        return deleted;
    }

    /**
     * 根据 documentId 查询向量数量
     *
     * @param documentId 文档ID
     * @return 向量数量
     */
    public int countByDocumentId(Long documentId) {
        String sql = """
                    SELECT COUNT(*) FROM %s
                    WHERE metadata->>'documentId' = ?
                """.formatted(VECTOR_TABLE);

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, documentId.toString());
        return count != null ? count : 0;
    }

    /**
     * 相似度搜索
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 相似文档列表
     */
    public List<Document> similaritySearch(String query, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return studyFriendPGvectorStore.similaritySearch(searchRequest);
    }

    /**
     * 带文档过滤的相似度搜索
     *
     * @param query      查询文本
     * @param topK       返回数量
     * @param documentId 限定文档ID
     * @return 相似文档列表
     */
    public List<Document> similaritySearchByDocument(String query, int topK, Long documentId) {
        var filterBuilder = new FilterExpressionBuilder();
        var filter = filterBuilder.eq("documentId", documentId.toString()).build();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filter)
                .build();

        return studyFriendPGvectorStore.similaritySearch(searchRequest);
    }

    /**
     * 检查向量表是否存在
     */
    public boolean isVectorTableExists() {
        String sql = """
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables
                        WHERE table_schema = 'public'
                        AND table_name = ?
                    )
                """;
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, VECTOR_TABLE);
        return Boolean.TRUE.equals(exists);
    }
}
