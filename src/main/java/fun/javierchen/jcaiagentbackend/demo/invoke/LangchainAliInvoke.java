package fun.javierchen.jcaiagentbackend.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;

/**
 * 整合了 dashscope 的 langchain4j
 */
public class LangchainAliInvoke {
    public static void main(String[] args) {
        String apiKey = System.getenv("JC_AI_AGENT_API_KEY");
        QwenChatModel qwenChatModel = QwenChatModel.builder().apiKey(apiKey).modelName("qwen-plus").build();
        String chatResult = qwenChatModel.chat("我是 JavierChen, 你是谁？");
        System.out.println(chatResult);
    }
}
