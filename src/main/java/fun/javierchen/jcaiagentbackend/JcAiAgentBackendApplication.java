package fun.javierchen.jcaiagentbackend;

import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * - exclude PgVectorStoreAutoConfiguration: 使用自定义的 PGVector 配置
 * - @EnableScheduling: 启用定时任务（用于失败重试）
 */
@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@EnableScheduling
public class JcAiAgentBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(JcAiAgentBackendApplication.class, args);
    }
}
