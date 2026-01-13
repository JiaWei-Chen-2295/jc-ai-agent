package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文档上传成功后的返回内容")
public record DocumentUploadResponse(
        @Schema(description = "文档唯一标识 ID", example = "1024")
        Long documentId,

        @Schema(description = "上传时的原始文件名", example = "course-notes.pdf")
        String filename,

        @Schema(description = "文档当前处理状态", example = "UPLOADED")
        String status,

        @Schema(description = "给前端的提示信息", example = "文件上传成功，正在后台处理中")
        String message
) {
}
