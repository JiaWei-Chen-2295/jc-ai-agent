package fun.javierchen.jcaiagentbackend.rag.config;

import fun.javierchen.jcaiagentbackend.rag.application.ingestion.enrichment.KeywordEnricher;
import fun.javierchen.jcaiagentbackend.rag.application.ingestion.loader.StudyFriendDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
@Slf4j
public class StudyFriendVectorStoreConfig {

    @Resource
    private StudyFriendDocumentLoader studyFriendDocumentLoader;

    @Resource
    private KeywordEnricher keywordEnricher;

    private String indexName = "study_friends";

//    @Bean
    @Deprecated
    VectorStore studyFriendVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        List<Document> documents = null;
        try {
            documents = studyFriendDocumentLoader.loadStudyPhotos();
        } catch (IOException e) {
            log.error("load photos error", e);
        }

        if (documents != null) {
            SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                    .build();
            // 使用 AI 为文档增加元信息
            // documents = keywordEnricher.enrich(documents);
            vectorStore.doAdd(documents);
            return vectorStore;
        }
        return null;
    }

    /**
     * PGVector 存储配置
     * 使用 @Lazy 确保只在首次使用时初始化，避免启动时调用 Embedding API 导致启动慢
     */
    @Bean
    @Lazy
    public VectorStore studyFriendPGvectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        log.info("初始化 PGVector 存储: tableName={}", indexName);
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536) // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE) // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW) // Optional: defaults to HNSW
                .initializeSchema(true) // Optional: defaults to false
                .schemaName("public") // Optional: defaults to "public"
                .vectorTableName(indexName) // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000) // Optional: defaults to 10000
                .build();
    }

}
