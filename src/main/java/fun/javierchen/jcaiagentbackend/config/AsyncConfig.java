package fun.javierchen.jcaiagentbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 配置文档索引专用线程池
 *
 * @author JavierChen
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * 文档索引专用线程池
     * <p>
     * 设计考虑：
     * - 核心线程数：2（不占用太多资源，Embedding API 有速率限制）
     * - 最大线程数：5（控制并发调用 Embedding API，避免触发限流）
     * - 队列容量：100（缓冲突发请求）
     * - 拒绝策略：CallerRunsPolicy（队列满时由调用者执行，实现背压控制）
     * </p>
     *
     * @return 线程池执行器
     */
    @Bean("documentIndexExecutor")
    public Executor documentIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-index-");
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：队列满时由调用者线程执行，实现背压
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("文档索引线程池初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
