package fun.javierchen.jcaiagentbackend.rag.application.ingestion.indexer;

import fun.javierchen.jcaiagentbackend.rag.application.ingestion.DocumentUploadedEvent;
import fun.javierchen.jcaiagentbackend.rag.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.rag.model.enums.DocumentStatus;
import fun.javierchen.jcaiagentbackend.rag.application.ingestion.parser.DocumentParser;
import fun.javierchen.jcaiagentbackend.rag.application.ingestion.parser.DocumentParserFactory;
import fun.javierchen.jcaiagentbackend.repository.StudyFriendDocumentRepository;
import fun.javierchen.jcaiagentbackend.rag.config.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档异步索引处理器
 * 监听文档上传事件，异步执行解析和向量化
 *
 * @author JavierChen
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentAsyncIndexer {

    private final VectorStoreService vectorStoreService;
    private final StudyFriendDocumentRepository documentRepository;
    private final DocumentParserFactory parserFactory;

    /**
     * 异步处理文档索引
     * <p>
     * 使用 @Async 确保在独立线程池中执行，不阻塞主流程
     * 使用 @EventListener 监听文档上传事件
     * </p>
     *
     * @param event 文档上传事件
     */
    @Async("documentIndexExecutor")
    @EventListener
    public void handleDocumentUpload(DocumentUploadedEvent event) {
        Long documentId = event.getDocumentId();
        log.info("开始异步索引文档: documentId={}, filename={}", documentId, event.getFilename());

        try {
            // 1. 更新状态为 INDEXING
            documentRepository.updateStatus(documentId, DocumentStatus.INDEXING, null);

            // 2. 解析文档为 Document 列表
            List<Document> documents = parseDocument(event);

            if (documents.isEmpty()) {
                log.warn("文档解析结果为空: documentId={}", documentId);
                documentRepository.updateStatus(documentId, DocumentStatus.FAILED, "文档解析结果为空");
                return;
            }

            // 3. 写入向量数据库（内部会添加 documentId 到 metadata）
            vectorStoreService.addDocuments(documents, documentId, event.getTenantId());

            // 4. 更新状态为 INDEXED
            documentRepository.updateStatus(documentId, DocumentStatus.INDEXED, null);
            log.info("文档索引成功: documentId={}, chunks={}", documentId, documents.size());

        } catch (Exception e) {
            log.error("文档索引失败: documentId={}", documentId, e);
            handleIndexFailure(documentId, e);
        }
    }

    /**
     * 解析文档（根据文件类型选择解析器）
     */
    private List<Document> parseDocument(DocumentUploadedEvent event) throws Exception {
        DocumentParser parser = parserFactory.getParser(event.getFileType());
        return parser.parse(event.getFilePath());
    }

    /**
     * 处理索引失败
     */
    private void handleIndexFailure(Long documentId, Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500) + "...";
        }
        documentRepository.updateStatus(documentId, DocumentStatus.FAILED, errorMessage);
    }

    /**
     * 重新索引文档（用于重试失败的文档）
     *
     * @param documentId 文档ID
     */
    public void reindexDocument(Long documentId) {
        StudyFriendDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));

        // 先删除已有的向量数据
        vectorStoreService.deleteByDocumentId(documentId, doc.getTenantId());

        // 重新发布事件触发索引
        DocumentUploadedEvent event = new DocumentUploadedEvent(
                this, documentId, doc.getTenantId(), doc.getOwnerUserId(),
                doc.getFilePath(), doc.getFileType(), doc.getFileName());
        handleDocumentUpload(event);
    }
}
