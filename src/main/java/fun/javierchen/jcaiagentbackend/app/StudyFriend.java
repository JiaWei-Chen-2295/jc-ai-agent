package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.advisor.AgentLoggerAdvisor;
import fun.javierchen.jcaiagentbackend.chatmemory.FileBasedChatMemory;
import fun.javierchen.jcaiagentbackend.rag.retrieval.HybridSearchVectorStore;
import jakarta.annotation.Resource;
import fun.javierchen.jcaiagentbackend.utils.VectorStoreFilterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
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

    /** 共享的会话记忆（按 chatId 隔离，跨模型复用） */
    private final ChatMemory chatMemory;
    private final ChatModelRegistry chatModelRegistry;

    private static final int RAG_TOP_K = 3;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.50;
    private static final List<String> RAG_SKIP_KEYWORDS = List.of(
            "出一个题", "出一道题", "出题", "考察我", "考考我", "练习题", "给我一道题", "测试我"
    );

    public StudyFriend(ChatModelRegistry chatModelRegistry) {
        this.chatModelRegistry = chatModelRegistry;
        String memoryDir = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "chat_memory";
        this.chatMemory = new FileBasedChatMemory(memoryDir);
    }

    @Resource
    private HybridSearchVectorStore hybridSearchVectorStore;

    // -------------------------------------------------------------------------
    // Public API — backward-compatible overloads delegate to 4-param variants
    // -------------------------------------------------------------------------

    public String doChatWithRAG(String chatMessage, String chatId) {
        return doChatWithRAG(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithRAG(String chatMessage, String chatId, Long tenantId) {
        return doChatWithRAG(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithRAG(String chatMessage, String chatId, Long tenantId, String modelId) {
        ChatClient client = buildChatClient(modelId);
        ChatResponse chatResponse;
        if (shouldUseRag(chatMessage)) {
            chatResponse = client.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .call().chatResponse();
        } else {
            chatResponse = client.prompt().user(chatMessage)
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
        return doChatWithRAGStream(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId, Long tenantId) {
        return doChatWithRAGStream(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId, Long tenantId, String modelId) {
        ChatClient client = buildChatClient(modelId);
        if (shouldUseRag(chatMessage)) {
            return client.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .stream().content();
        }
        return client.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
                .stream().content();
    }

    public String doChatWithTools(String chatMessage, String chatId) {
        return doChatWithTools(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithTools(String chatMessage, String chatId, Long tenantId) {
        return doChatWithTools(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithTools(String chatMessage, String chatId, Long tenantId, String modelId) {
        ChatClient client = buildChatClient(modelId);
        ChatResponse chatResponse;
        if (shouldUseRag(chatMessage)) {
            chatResponse = client.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .call().chatResponse();
        } else {
            chatResponse = client.prompt().user(chatMessage)
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

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId) {
        return doChatWithRAGStreamTool(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId, Long tenantId) {
        return doChatWithRAGStreamTool(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId, Long tenantId, String modelId) {
        ChatClient client = buildChatClient(modelId);
        if (shouldUseRag(chatMessage)) {
            return client.prompt().user(chatMessage)
                    .system(SYSTEM_PROMPT)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .stream().content();
        }
        return client.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
                .stream().content();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * 根据 modelId 从注册中心解析 ChatModel，并构建轻量级 ChatClient
     * （ChatMemory 在所有模型间共享，按 chatId 隔离对话上下文，切换模型后记忆不丢失）
     */
    private ChatClient buildChatClient(String modelId) {
        ChatModel chatModel = chatModelRegistry.resolve(modelId);
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new AgentLoggerAdvisor()
                )
                .build();
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

    private QuestionAnswerAdvisor buildRagAdvisor(String chatMessage, Long tenantId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(chatMessage)
                .topK(RAG_TOP_K)
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD);
        if (tenantId != null) {
            Filter.Expression filter = VectorStoreFilterUtils.buildTenantIdFilter(tenantId);
            builder.filterExpression(filter);
        }
        SearchRequest searchRequest = builder.build();
        return new QuestionAnswerAdvisor(hybridSearchVectorStore, searchRequest);
    }
}

