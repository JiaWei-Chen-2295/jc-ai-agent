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
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.io.File;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Slf4j
@Component
public class StudyFriend {

    private final String SYSTEM_PROMPT = """
            你是一个助手，你帮助用户解决学习上的各种问题
            """;

    private final ChatClient chatClient;

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
    private VectorStore studyFriendVectorStore;

    public String doChatWithRAG(String chatMessage, String chatId) {
        ChatResponse chatResponse = chatClient.prompt().user(chatMessage)
                .system(SYSTEM_PROMPT)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new AgentLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(studyFriendVectorStore))
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ai content: {}", content);
        return content;
    }
}
