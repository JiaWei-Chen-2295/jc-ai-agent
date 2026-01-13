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

    public DocumentUploadedEvent(Object source, Long documentId, String filePath, String fileType, String filename) {
        super(source);
        this.documentId = documentId;
        this.filePath = filePath;
        this.fileType = fileType;
        this.filename = filename;
    }
}
