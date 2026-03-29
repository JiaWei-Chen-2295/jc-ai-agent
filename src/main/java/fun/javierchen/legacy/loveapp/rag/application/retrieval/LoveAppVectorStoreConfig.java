package fun.javierchen.legacy.loveapp.rag.application.retrieval;

import fun.javierchen.jcaiagentbackend.rag.application.ingestion.enrichment.KeywordEnricher;
import fun.javierchen.legacy.loveapp.rag.application.ingestion.loader.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.util.List;

@Slf4j
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private KeywordEnricher keywordEnricher;

    public VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        List<Document> documents = null;
        try {
            documents = loveAppDocumentLoader.loadMarkDowns();
        } catch (IOException e) {
            log.error("load markdowns error", e);
        }

        if (documents != null) {
            SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                    .build();

            // 使用 AI 为文档增加元信息
//            documents = keywordEnricher.enrich(documents);

            vectorStore.doAdd(documents);
            return vectorStore;
        }
        return null;
    }


}
