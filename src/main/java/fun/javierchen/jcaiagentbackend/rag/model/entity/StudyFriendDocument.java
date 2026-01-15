package fun.javierchen.jcaiagentbackend.rag.model.entity;

import fun.javierchen.jcaiagentbackend.rag.model.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习助手文档实体
 * 对应表 study_friend_document
 *
 * @author JavierChen
 */
@Data
@Schema(description = "学习助手文档信息")
public class StudyFriendDocument {

    /**
     * 主键ID
     */
    @Schema(description = "主键 ID", example = "1")
    private Long id;

    /**
     * 租户 ID
     */
    @Schema(description = "租户 ID", example = "100")
    private Long tenantId;

    /**
     * 归属用户 ID
     */
    @Schema(description = "归属用户 ID", example = "1")
    private Long ownerUserId;

    /**
     * 文件名
     */
    @Schema(description = "文件名", example = "demo.pdf")
    private String fileName;

    /**
     * 文件存储路径
     */
    @Schema(description = "文件存储路径（相对路径）", example = "/upload_file/demo.pdf")
    private String filePath;

    /**
     * 文件类型 (pdf/pptx/docx/image/md)
     */
    @Schema(description = "文件类型 (pdf/pptx/docx/image/md)", example = "pdf")
    private String fileType;

    /**
     * 文档处理状态：UPLOADED | INDEXING | INDEXED | FAILED
     */
    @Schema(description = "文档处理状态：UPLOADED | INDEXING | INDEXED | FAILED", example = "INDEXED")
    private DocumentStatus status;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因，仅在处理失败时返回", example = "解析失败：不支持的格式")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2025-01-12T08:00:00")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2025-01-12T08:10:00")
    private LocalDateTime updatedAt;
}
