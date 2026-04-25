package fun.javierchen.jcaiagentbackend;

import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * - @EnableScheduling: 启用定时任务（用于失败重试）
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(VoiceProperties.class)
public class JcAiAgentBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(JcAiAgentBackendApplication.class, args);
    }
}
