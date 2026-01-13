package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.event.DocumentUploadedEvent;
import fun.javierchen.jcaiagentbackend.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.model.enums.DocumentStatus;
import fun.javierchen.jcaiagentbackend.repository.StudyFriendDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 文档上传服务
 * 处理文件上传、保存，并发布事件触发异步索引
 *
 * @author JavierChen
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentUploadService {

    private final StudyFriendDocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final String UPLOAD_DIR = "upload_file";

    /**
     * 上传文档 - 立即返回，异步索引
     *
     * @param filename  文件名
     * @param fileType  文件类型
     * @param fileBytes 文件字节
     * @return 文档ID
     */
    @Transactional
    public Long uploadDocument(String filename, String fileType, byte[] fileBytes) throws IOException {
        // 1. 检查是否已存在相同文件（可选的去重逻辑）
        StudyFriendDocument existing = documentRepository.findByFileNameAndType(filename, fileType);
        if (existing != null && existing.getStatus() == DocumentStatus.INDEXED) {
            log.info("文档已存在且已索引，返回现有文档: id={}, filename={}", existing.getId(), filename);
            return existing.getId();
        }

        // 2. 保存文件到磁盘
        String fileId = UUID.randomUUID().toString();
        // todo: 安全问题
        Path filePath = Paths.get(UPLOAD_DIR, fileId, filename);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, fileBytes);

        log.info("文件保存成功: path={}", filePath);

        // 3. 保存文档记录（状态：UPLOADED）
        StudyFriendDocument document = new StudyFriendDocument();
        document.setFileName(filename);
        document.setFilePath(filePath.toString());
        document.setFileType(fileType);
        document.setStatus(DocumentStatus.UPLOADED);
        documentRepository.save(document);

        log.info("文档记录创建成功: id={}, filename={}", document.getId(), filename);

        // 4. 发布事件（触发异步索引）
        eventPublisher.publishEvent(new DocumentUploadedEvent(
                this, document.getId(), filePath.toString(), fileType, filename));

        return document.getId();
    }

    /**
     * 获取文档状态
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    public StudyFriendDocument getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
    }

    /**
     * 获取所有文档
     */
    public List<StudyFriendDocument> getAllDocuments() {
        return documentRepository.findAll();
    }

    /**
     * 删除文档（包括向量数据）
     *
     * @param documentId 文档ID
     */
    @Transactional
    public void deleteDocument(Long documentId, VectorStoreService vectorStoreService) {
        StudyFriendDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("文档不存在，跳过删除: id={}", documentId);
            return;
        }

        // 删除向量数据
        vectorStoreService.deleteByDocumentId(documentId);

        // 删除文件
        try {
            Path filePath = Paths.get(doc.getFilePath());
            Files.deleteIfExists(filePath);
            // 尝试删除父目录（如果为空）
            Files.deleteIfExists(filePath.getParent());
        } catch (IOException e) {
            log.warn("删除文件失败: path={}", doc.getFilePath(), e);
        }

        // 删除数据库记录
        documentRepository.deleteById(documentId);
        log.info("文档删除成功: id={}", documentId);
    }
}
