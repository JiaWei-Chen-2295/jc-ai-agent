package fun.javierchen.jcaiagentbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * - @EnableScheduling: 启用定时任务（用于失败重试）
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class JcAiAgentBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(JcAiAgentBackendApplication.class, args);
    }
}
