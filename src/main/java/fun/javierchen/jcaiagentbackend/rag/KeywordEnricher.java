package fun.javierchen.jcaiagentbackend.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用于封装增强文档的元数据
 */
@Component
public class KeywordEnricher {

    @Resource
    private ChatModel dashscopeChatModel;

    public List<Document> enrich(List<Document> documentList) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);
        return keywordMetadataEnricher.apply(documentList);
    }
}
