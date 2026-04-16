package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.advisor.AgentLoggerAdvisor;
import fun.javierchen.jcaiagentbackend.chatmemory.FileBasedChatMemory;
import fun.javierchen.jcaiagentbackend.rag.retrieval.HybridSearchVectorStore;
import fun.javierchen.jcaiagentbackend.websearch.FirecrawlMcpSearchService;
import fun.javierchen.jcaiagentbackend.websearch.FirecrawlSearchResult;
import fun.javierchen.jcaiagentbackend.websearch.WebSearchProperties;
import jakarta.annotation.Resource;
import fun.javierchen.jcaiagentbackend.utils.VectorStoreFilterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;



@Slf4j
@Component
public class StudyFriend {

    private final String SYSTEM_PROMPT = """
            你是一个助手，你帮助用户解决学习上的各种问题
            """;

    /** 共享的会话记忆（按 chatId 隔离，跨模型复用） */
    private final ChatMemory chatMemory;
    private final ChatModelRegistry chatModelRegistry;
    private final FirecrawlMcpSearchService firecrawlMcpSearchService;
    private final WebSearchProperties webSearchProperties;

    private static final int RAG_TOP_K = 3;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.50;
    private static final List<String> RAG_SKIP_KEYWORDS = List.of(
            "出一个题", "出一道题", "出题", "考察我", "考考我", "练习题", "给我一道题", "测试我"
    );

    public StudyFriend(ChatModelRegistry chatModelRegistry,
                       FirecrawlMcpSearchService firecrawlMcpSearchService,
                       WebSearchProperties webSearchProperties) {
        this.chatModelRegistry = chatModelRegistry;
        this.firecrawlMcpSearchService = firecrawlMcpSearchService;
        this.webSearchProperties = webSearchProperties;
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
        return doChatWithRAG(chatMessage, chatId, tenantId, modelId, false).content();
    }

    public StudyFriendChatResult doChatWithRAG(String chatMessage, String chatId, Long tenantId, String modelId, boolean webSearchEnabled) {
        return call(chatMessage, chatId, tenantId, modelId, webSearchEnabled);
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId) {
        return doChatWithRAGStream(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId, Long tenantId) {
        return doChatWithRAGStream(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId, Long tenantId, String modelId) {
        return doChatWithRAGStream(chatMessage, chatId, tenantId, modelId, false).contentStream();
    }

    public StudyFriendStreamResult doChatWithRAGStream(String chatMessage, String chatId, Long tenantId, String modelId, boolean webSearchEnabled) {
        FirecrawlSearchResult searchResult = resolveWebSearch(webSearchEnabled, chatMessage);
        String promptMessage = buildUserPrompt(chatMessage, searchResult);
        ChatClient client = buildChatClient(modelId);
        Flux<String> contentStream;
        if (shouldUseRag(chatMessage)) {
            contentStream = client.prompt().user(promptMessage)
                    .system(buildSystemPrompt(searchResult))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .stream().content();
        } else {
            contentStream = client.prompt().user(promptMessage)
                    .system(buildSystemPrompt(searchResult))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new AgentLoggerAdvisor())
                    .stream().content();
        }
        return new StudyFriendStreamResult(contentStream, searchResult.hasSources(), searchResult.sources());
    }

    public String doChatWithTools(String chatMessage, String chatId) {
        return doChatWithTools(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithTools(String chatMessage, String chatId, Long tenantId) {
        return doChatWithTools(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public String doChatWithTools(String chatMessage, String chatId, Long tenantId, String modelId) {
        return doChatWithTools(chatMessage, chatId, tenantId, modelId, false).content();
    }

    public StudyFriendChatResult doChatWithTools(String chatMessage, String chatId, Long tenantId, String modelId, boolean webSearchEnabled) {
        return call(chatMessage, chatId, tenantId, modelId, webSearchEnabled);
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId) {
        return doChatWithRAGStreamTool(chatMessage, chatId, TenantContextHolder.getTenantId(), ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId, Long tenantId) {
        return doChatWithRAGStreamTool(chatMessage, chatId, tenantId, ChatModelRegistry.DEFAULT_MODEL_ID);
    }

    public Flux<String> doChatWithRAGStreamTool(String chatMessage, String chatId, Long tenantId, String modelId) {
        return doChatWithRAGStreamTool(chatMessage, chatId, tenantId, modelId, false).contentStream();
    }

    public StudyFriendStreamResult doChatWithRAGStreamTool(String chatMessage, String chatId, Long tenantId, String modelId, boolean webSearchEnabled) {
        return doChatWithRAGStream(chatMessage, chatId, tenantId, modelId, webSearchEnabled);
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
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new AgentLoggerAdvisor()
                )
                .build();
    }

    public boolean resolveWebSearchEnabled(Boolean requestedEnabled) {
        return requestedEnabled != null ? requestedEnabled : webSearchProperties.isEnabled();
    }

    private StudyFriendChatResult call(String chatMessage, String chatId, Long tenantId, String modelId, boolean webSearchEnabled) {
        FirecrawlSearchResult searchResult = resolveWebSearch(webSearchEnabled, chatMessage);
        String promptMessage = buildUserPrompt(chatMessage, searchResult);
        ChatClient client = buildChatClient(modelId);
        ChatResponse chatResponse;
        if (shouldUseRag(chatMessage)) {
            chatResponse = client.prompt().user(promptMessage)
                    .system(buildSystemPrompt(searchResult))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new AgentLoggerAdvisor())
                    .advisors(buildRagAdvisor(chatMessage, tenantId))
                    .call().chatResponse();
        } else {
            chatResponse = client.prompt().user(promptMessage)
                    .system(buildSystemPrompt(searchResult))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new AgentLoggerAdvisor())
                    .call().chatResponse();
        }
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return new StudyFriendChatResult(content, searchResult.hasSources(), searchResult.sources());
    }

    private FirecrawlSearchResult resolveWebSearch(boolean webSearchEnabled, String chatMessage) {
        if (!webSearchEnabled) {
            return FirecrawlSearchResult.empty();
        }
        return firecrawlMcpSearchService.search(chatMessage);
    }

    private String buildSystemPrompt(FirecrawlSearchResult searchResult) {
        if (!searchResult.hasSources()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n如果系统附带了联网搜索结果，请优先结合这些网页资料回答，并保持结论最新、准确。";
    }

    private String buildUserPrompt(String chatMessage, FirecrawlSearchResult searchResult) {
        if (!searchResult.hasSources()) {
            return chatMessage;
        }
        return """
                用户原始问题：
                %s

                以下是系统通过 FireCrawl MCP 检索到的网页资料，请优先结合这些资料回答。
                不要在正文中重复罗列所有链接，来源链接会由系统单独返回给前端。

                %s
                """.formatted(chatMessage, searchResult.context());
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

    private Advisor buildRagAdvisor(String chatMessage, Long tenantId) {
        VectorStoreDocumentRetriever.Builder builder = VectorStoreDocumentRetriever.builder()
                .vectorStore(hybridSearchVectorStore)
                .topK(RAG_TOP_K)
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD);
        if (tenantId != null) {
            Filter.Expression filter = VectorStoreFilterUtils.buildTenantIdFilter(tenantId);
            builder.filterExpression(filter);
        }
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(builder.build())
                .build();
    }
}

