package fun.javierchen.jcaiagentbackend.rag.task;

import fun.javierchen.jcaiagentbackend.rag.application.ingestion.DocumentUploadedEvent;
import fun.javierchen.jcaiagentbackend.rag.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.rag.model.enums.DocumentStatus;
import fun.javierchen.jcaiagentbackend.repository.StudyFriendDocumentRepository;
import fun.javierchen.jcaiagentbackend.rag.config.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档索引重试任务
 * 定时检查失败的文档并重试索引
 *
 * @author JavierChen
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentIndexRetryTask {

    private final StudyFriendDocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final VectorStoreService vectorStoreService;

    /**
     * 最大重试次数
     * 超过此次数的文档不再自动重试，需要人工介入
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * INDEXING 状态超时时间（分钟）
     * 超过此时间仍为 INDEXING 状态的文档视为处理失败
     */
    private static final int INDEXING_TIMEOUT_MINUTES = 10;

    /**
     * 定时重试失败的文档
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void retryFailedDocuments() {
        log.debug("开始执行文档索引重试任务...");

        // 1. 查询失败的文档
        List<StudyFriendDocument> failedDocuments = documentRepository.findFailedDocuments(10);

        if (failedDocuments.isEmpty()) {
            log.debug("没有需要重试的失败文档");
            return;
        }

        log.info("发现 {} 个失败文档需要重试", failedDocuments.size());

        for (StudyFriendDocument doc : failedDocuments) {
            try {
                retryDocument(doc);
            } catch (Exception e) {
                log.error("重试文档失败: id={}", doc.getId(), e);
            }
        }
    }

    /**
     * 检查并恢复超时的 INDEXING 状态文档
     * 每10分钟执行一次
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void recoverTimeoutDocuments() {
        log.debug("开始检查超时的 INDEXING 文档...");

        List<StudyFriendDocument> timeoutDocuments = documentRepository
                .findTimeoutIndexingDocuments(INDEXING_TIMEOUT_MINUTES);

        if (timeoutDocuments.isEmpty()) {
            log.debug("没有超时的 INDEXING 文档");
            return;
        }

        log.warn("发现 {} 个超时的 INDEXING 文档，标记为 FAILED", timeoutDocuments.size());

        for (StudyFriendDocument doc : timeoutDocuments) {
            documentRepository.updateStatus(doc.getId(), DocumentStatus.FAILED, "处理超时");
        }
    }

    /**
     * 重试单个文档
     */
    private void retryDocument(StudyFriendDocument doc) {
        log.info("重试文档索引: id={}, filename={}", doc.getId(), doc.getFileName());

        // 先删除可能存在的部分向量数据
        try {
            vectorStoreService.deleteByDocumentId(doc.getId());
        } catch (Exception e) {
            log.warn("清理向量数据失败: id={}", doc.getId(), e);
        }

        // 发布事件触发重新索引
        eventPublisher.publishEvent(new DocumentUploadedEvent(
                this,
                doc.getId(),
                doc.getFilePath(),
                doc.getFileType(),
                doc.getFileName()));
    }
}
