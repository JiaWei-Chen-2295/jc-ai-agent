package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.controller.dto.DocumentUploadResponse;
import fun.javierchen.jcaiagentbackend.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.model.enums.DocumentStatus;
import fun.javierchen.jcaiagentbackend.service.DocumentUploadService;
import fun.javierchen.jcaiagentbackend.service.VectorStoreService;
import fun.javierchen.jcaiagentbackend.service.indexer.DocumentAsyncIndexer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档管理控制器
 * 提供文档上传、查询、删除等接口
 *
 * @author JavierChen
 */
@RestController
@RequestMapping("/api/document")
@Tag(name = "文档管理", description = "文档上传与索引管理接口")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final VectorStoreService vectorStoreService;
    private final DocumentAsyncIndexer documentAsyncIndexer;

    /**
     * 上传文档
     * 文件上传后立即返回，后台异步进行向量化处理
     *
     * @param file 上传的文件
     * @return 文档ID
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "上传文档",
            description = "上传文档后立即返回，后台异步进行向量化处理。返回的 documentId 可用于查询状态。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DocumentUploadResponse.class),
                            examples = @ExampleObject(value = "{\"code\":0,\"data\":{\"documentId\":1001,\"filename\":\"demo.pdf\",\"status\":\"UPLOADED\",\"message\":\"文件上传成功，正在后台处理中\"},\"message\":\"ok\"}")
                    )),
            @ApiResponse(responseCode = "400", description = "文件为空或读取失败")
    })
    public BaseResponse<DocumentUploadResponse> uploadDocument(
            @Parameter(description = "上传的文档文件，支持 pdf/pptx/docx/md 等", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            return ResultUtils.error(400, "文件不能为空");
        }

        String filename = file.getOriginalFilename();
        String fileType = extractFileType(filename);
        byte[] fileBytes = file.getBytes();

        Long documentId = documentUploadService.uploadDocument(filename, fileType, fileBytes);

        DocumentUploadResponse result = new DocumentUploadResponse(
                documentId,
                filename,
                DocumentStatus.UPLOADED.getCode(),
                "文件上传成功，正在后台处理中"
        );

        return ResultUtils.success(result);
    }

    /**
     * 查询文档状态
     *
     * @param documentId 文档ID
     * @return 文档信息
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "查询文档状态", description = "根据文档ID查询文档处理状态")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "文档详情",
                    content = @Content(schema = @Schema(implementation = StudyFriendDocument.class)))
    })
    public BaseResponse<StudyFriendDocument> getDocument(
            @Parameter(description = "文档唯一标识 ID", required = true, example = "1001")
            @PathVariable Long documentId) {
        StudyFriendDocument document = documentUploadService.getDocument(documentId);
        return ResultUtils.success(document);
    }

    /**
     * 查询所有文档
     *
     * @return 文档列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询所有文档", description = "获取所有文档列表及其状态")
    @ApiResponse(responseCode = "200", description = "文档列表",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = StudyFriendDocument.class))))
    public BaseResponse<List<StudyFriendDocument>> listDocuments() {
        List<StudyFriendDocument> documents = documentUploadService.getAllDocuments();
        return ResultUtils.success(documents);
    }

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     * @return 操作结果
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档", description = "删除文档及其关联的向量数据")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"code\":0,\"data\":\"文档删除成功\",\"message\":\"ok\"}")
            ))
    })
    public BaseResponse<String> deleteDocument(
            @Parameter(description = "文档唯一标识 ID", required = true, example = "1001")
            @PathVariable Long documentId) {
        documentUploadService.deleteDocument(documentId, vectorStoreService);
        return ResultUtils.success("文档删除成功");
    }

    /**
     * 重新索引文档
     *
     * @param documentId 文档ID
     * @return 操作结果
     */
    @PostMapping("/{documentId}/reindex")
    @Operation(summary = "重新索引文档", description = "重新执行文档的向量化处理（用于失败重试）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "提交成功", content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"code\":0,\"data\":\"重新索引任务已提交\",\"message\":\"ok\"}")
            ))
    })
    public BaseResponse<String> reindexDocument(
            @Parameter(description = "文档唯一标识 ID", required = true, example = "1001")
            @PathVariable Long documentId) {
        documentAsyncIndexer.reindexDocument(documentId);
        return ResultUtils.success("重新索引任务已提交");
    }

    /**
     * 从文件名中提取文件类型
     */
    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
