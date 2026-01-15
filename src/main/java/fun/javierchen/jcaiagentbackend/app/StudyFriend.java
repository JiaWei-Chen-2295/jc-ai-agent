package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.advisor.AgentLoggerAdvisor;
import fun.javierchen.jcaiagentbackend.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Slf4j
@Component
public class StudyFriend {

    private final String SYSTEM_PROMPT = """
            你是一个助手，你帮助用户解决学习上的各种问题
            """;

    private final ChatClient chatClient;
    private static final int RAG_TOP_K = 3;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.75;
    private static final List<String> RAG_SKIP_KEYWORDS = List.of(
            "出一个题", "出一道题", "出题", "考察我", "考考我", "练习题", "给我一道题", "测试我"
    );

    /**
     * 初始化对话模型
     *
     * @param dashscopeChatModel
     */
    public StudyFriend(ChatModel dashscopeChatModel) {

        String memoryDir = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "chat_memory";

        ChatMemory chatMemory = new FileBasedChatMemory(memoryDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        // 基于内存的记忆方式
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 基于文件的记忆方式
                        // 添加日志记录功能
                        new AgentLoggerAdvisor()
                )
                .build();
    }

    @Resource
    private VectorStore studyFriendPGvectorStore;

    public String doChatWithRAG(String chatMessage, String chatId) {
        ChatResponse chatResponse;
        if (shouldUseRag(chatMessage)) {
            chatResponse = chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage))
                    .call().chatResponse();
        } else {
            chatResponse = chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .call().chatResponse();
        }
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId) {
        if (shouldUseRag(chatMessage)) {
            return chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage))
                    .stream().content();
        }
        return chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
                .stream().content();
    }

//    @Resource
//    private ToolCallback[] toolCallback;
    public String doChatWithTools(String chatMessage, String chatId) {
        ChatResponse chatResponse;
        if (shouldUseRag(chatMessage)) {
            chatResponse = chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage))
//                .tools(toolCallback)
                    .call().chatResponse();
        } else {
            chatResponse = chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
//                .tools(toolCallback)
                    .call().chatResponse();
        }
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId) {
        if (shouldUseRag(chatMessage)) {
            return chatClient.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage))
//                .tools(toolCallback)
                    .stream().content();
        }
        return chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
//                .tools(toolCallback)
                .stream().content();
    }

    private boolean shouldUseRag(String chatMessage) {
        if (!StringUtils.hasText(chatMessage)) {
            return false;
        }
        for (String keyword : RAG_SKIP_KEYWORDS) {
            if (chatMessage.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private QuestionAnswerAdvisor buildRagAdvisor(String chatMessage) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(chatMessage)
                .topK(RAG_TOP_K)
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                .build();
        return new QuestionAnswerAdvisor(studyFriendPGvectorStore, searchRequest);
    }
}
