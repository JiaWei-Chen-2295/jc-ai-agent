package fun.javierchen.jcaiagentbackend.rag.application.ingestion;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文档上传完成事件
 * 用于触发异步向量化处理
 *
 * @author JavierChen
 */
@Getter
public class DocumentUploadedEvent extends ApplicationEvent {

    /**
     * 文档ID（数据库主键）
     */
    private final Long documentId;

    /**
     * 租户ID
     */
    private final Long tenantId;

    /**
     * 归属用户ID
     */
    private final Long ownerUserId;

    /**
     * 文件存储路径
     */
    private final String filePath;

    /**
     * 文件类型
     */
    private final String fileType;

    /**
     * 文件名
     */
    private final String filename;

    public DocumentUploadedEvent(Object source, Long documentId, Long tenantId, Long ownerUserId,
                                 String filePath, String fileType, String filename) {
        super(source);
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.filePath = filePath;
        this.fileType = fileType;
        this.filename = filename;
    }
}
