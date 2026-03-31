package fun.javierchen.jcaiagentbackend.agent.quiz.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测验 Agent 配置类
 *
 * @author JavierChen
 */
@Configuration
public class QuizAgentConfig {

    /**
     * 配置 ChatClient Builder
     * 使用 Spring AI Alibaba 提供的 ChatModel
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashScopeChatModel") org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
