package fun.javierchen.jcaiagentbackend;

import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScans;

// 仅在需要使用 PostgreSQL 作为文档向量数据库 时才需要引入
@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class )
public class JcAiAgentBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(JcAiAgentBackendApplication.class, args);
    }
}
