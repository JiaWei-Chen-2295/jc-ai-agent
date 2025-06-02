package fun.javierchen.jcaiagentbackend.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多查询扩展
 */
@Component
public class MyMultiQueryExpander {

    private final ChatClient.Builder chatClientBuilder;

    public MyMultiQueryExpander(ChatModel dashscopeChatModel) {
        chatClientBuilder = ChatClient.builder(dashscopeChatModel);
    }

    public List<Query> expand(Query singleQuery) {
        MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                // 节省资源
                .numberOfQueries(5)
                // 保留原始语义
                .includeOriginal(true)
                .build();
        return multiQueryExpander.expand(singleQuery);
    }
}
