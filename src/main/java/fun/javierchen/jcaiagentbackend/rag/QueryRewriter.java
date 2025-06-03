package fun.javierchen.jcaiagentbackend.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器
 * 修改用户的提示词使其输出效果更好
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        queryTransformer = RewriteQueryTransformer.builder().chatClientBuilder(
                        ChatClient.builder(dashscopeChatModel).defaultOptions(
                                // 大多数聊天模型的默认温度通常过高，不利于查询转换，导致检索效果降低。
                                ChatOptions.builder().temperature(0.0).build())
                )
                .build();
    }

    public String rewrite(String prompt) {
        Query query = new Query(prompt);
        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery.text();
    }
}
