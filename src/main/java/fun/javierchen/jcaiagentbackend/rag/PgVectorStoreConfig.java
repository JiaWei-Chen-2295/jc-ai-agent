package fun.javierchen.jcaiagentbackend.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

// 当需要使用这个 Bean 时  需要将 LoveAppVectorStoreConfig 配置类注释掉
@Configuration
public class PgVectorStoreConfig {


    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    public PgVectorStore pgVectorStore(EmbeddingModel dashscopeEmbeddingModel) throws IOException {
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                //      .dimensions(1536)                    // 向量维度 按照我们当前的 EmbeddingModel 进行设置
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        List<Document> documentList = loveAppDocumentLoader.loadMarkDowns();
        store.doAdd(documentList);
        return store;
    }

}
