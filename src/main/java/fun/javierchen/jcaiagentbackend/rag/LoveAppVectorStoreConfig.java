package fun.javierchen.jcaiagentbackend.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

//@Configuration
@Slf4j
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    VectorStore LoveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        List<Document> documents = null;
        try {
            documents = loveAppDocumentLoader.loadMarkDowns();
        } catch (IOException e) {
            log.error("load markdowns error", e);
        }

        if (documents != null) {
            SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
            vectorStore.doAdd(documents);
            return vectorStore;
        }
        return null;
    }


}
