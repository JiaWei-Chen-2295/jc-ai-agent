package fun.javierchen.jcaiagentbackend.rag.elasticsearch.sync;

import fun.javierchen.jcaiagentbackend.rag.elasticsearch.service.EsKeywordSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EsDocumentSyncStartupRunner implements ApplicationRunner {

    private final PgToEsSyncService pgToEsSyncService;
    private final EsKeywordSearchService esKeywordSearchService;

    @Value("${jc-ai-agent.rag.hybrid-search.enabled:true}")
    private boolean hybridSearchEnabled;

    @Value("${jc-ai-agent.rag.hybrid-search.sync-on-startup:false}")
    private boolean syncOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!hybridSearchEnabled || !syncOnStartup) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            esKeywordSearchService.ensureIndexReady();
            int synced = pgToEsSyncService.fullSync();
            long durationMs = System.currentTimeMillis() - start;
            log.info("启动时 PG -> ES 全量同步完成: chunkCount={}, durationMs={}", synced, durationMs);
        } catch (Exception e) {
            log.warn("启动自动同步失败，服务继续启动: {}", e.getMessage());
        }
    }
}
