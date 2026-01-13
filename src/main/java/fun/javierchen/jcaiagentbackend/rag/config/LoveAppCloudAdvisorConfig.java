package fun.javierchen.jcaiagentbackend.rag.config;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import fun.javierchen.jcaiagentbackend.rag.love.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class LoveAppCloudAdvisorConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public Advisor loveAppCloudAdvisor() {

        final String INDEX_NAME = "恋爱ing";

        DashScopeApi dashScopeApi = new DashScopeApi(apiKey);
        DocumentRetriever dashScopeDocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(INDEX_NAME)
                        .build()
        );

        // 使用 Spring AI 的检索增强顾问(advisor)来处理 DashScopeDocumentRetriever 切好片的文档
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(dashScopeDocumentRetriever)
                .build();

    }

    // 当需要使用这个 Bean 时  需要将 LoveAppVectorStoreConfig 配置类注释掉
    // 反之同理
    @Configuration
    public static class PgVectorStoreConfig {


        @Resource
        private JdbcTemplate jdbcTemplate;

        @Resource
        private LoveAppDocumentLoader loveAppDocumentLoader;

        @Bean
        public PgVectorStore pgVectorStore(EmbeddingModel dashscopeEmbeddingModel) throws IOException {
            PgVectorStore store = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
//                          .dimensions(1536)                    // 向量维度 按照我们当前的 EmbeddingModel 进行设置
                    .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                    .indexType(HNSW)                     // Optional: defaults to HNSW
//                    .initializeSchema(true)              // Optional: defaults to false
                    .schemaName("public")                // Optional: defaults to "public"
                    .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                    .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                    .build();
            List<Document> documentList = loveAppDocumentLoader.loadMarkDowns();
            store.doAdd(documentList);
            return store;
        }

    }
}
