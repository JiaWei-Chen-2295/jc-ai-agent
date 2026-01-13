package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.advisor.AgentLoggerAdvisor;
import fun.javierchen.jcaiagentbackend.chatmemory.FileBasedChatMemory;
import fun.javierchen.jcaiagentbackend.rag.application.retrieval.AlibabaMachineTranslationQueryTransformer;
import fun.javierchen.jcaiagentbackend.rag.application.retrieval.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Slf4j
@Component
public class LoveApp {

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    private ChatClient chatClient;

    /**
     * 初始化对话模型
     *
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {

        String memoryDir = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "chat_memory";

        // 初始化内存记忆
//        ChatMemory chatMemory = new InMemoryChatMemory();
        // 添加文件记忆方式
        ChatMemory chatMemory = new FileBasedChatMemory(memoryDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        // 基于内存的记忆方式
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 基于文件的记忆方式
                        // 添加日志记录功能
                        new AgentLoggerAdvisor()
                        // 添加重读功能 会增大 Token 的消费量
                        // ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * 获取对话的结果 支持多轮对话
     *
     * @param chatMessage
     * @param chatId
     * @return
     */
    public String doChat(String chatMessage, String chatId) {
        ChatResponse chatResponse = chatClient.prompt().user(chatMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * 每次对话都生成一份恋爱建议报告
     *
     * @param chatMessage
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String chatMessage, String chatId) {
        LoveReport loveReport = chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT + "每次对话结束都要生成恋爱报告，标题为{用户名}的恋爱报告，内容为建议列表")
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call().entity(LoveReport.class);
        log.info("ai generate report: {}", loveReport);
        return loveReport;
    }

//    @Resource
//    private VectorStore loveAppVectorStore;
//    @Resource
//    private Advisor loveAppCloudAdvisor;
    @Resource
    private VectorStore pgVectorStore;
    @Resource
    private QueryRewriter queryRewriter;

    public String doChatWithRAG(String chatMessage, String chatId) {
        String rewritePrompt = queryRewriter.rewrite(chatMessage);
        ChatResponse chatResponse = chatClient.prompt().user(rewritePrompt)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 通过云端文档检索功能
//                .advisors(loveAppCloudAdvisor)
                .advisors(new QuestionAnswerAdvisor(pgVectorStore))
                // 配置 RAG
//                .advisors(LoveAppRagCustomAdvisorFactory.create(loveAppVectorStore, LoveAppMetaDataStatusEnum.SINGLE_PERSON))
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);



        return content;
    }

    @Resource
    private AlibabaMachineTranslationQueryTransformer alibabaMachineTranslationQueryTransformer;
    public String doChatWithMultiLanguage(String chatMessage, String chatId, String language) {
        alibabaMachineTranslationQueryTransformer.setTargetLanguage(language);
        String rewritePrompt = alibabaMachineTranslationQueryTransformer.transform(new Query(chatMessage)).text();
        ChatResponse chatResponse = chatClient.prompt().user(rewritePrompt)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }

    public void doChatWithStream(String chatMessage, String chatId, java.util.function.Consumer<String> onSubscribeChunk, Runnable onComplete) {
        Flux<String> contentFlux = chatClient.prompt().system(SYSTEM_PROMPT).user(chatMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream().content();


        contentFlux.subscribe(onSubscribeChunk,
                throwable -> log.error("Error: {}", throwable.getMessage()),
                onComplete);
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTool(String chatMessage, String chatId) {
        ChatResponse chatResponse = chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .tools(allTools)
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    public String doChatWithMCP(String chatMessage, String chatId) {
        ChatResponse chatResponse = chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .tools(toolCallbackProvider)
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }


}
