package fun.javierchen.jcaiagentbackend.controller;

import cn.hutool.core.lang.Assert;
import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.FileMetadata;
import fun.javierchen.jcaiagentbackend.service.FileService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(value = "/file")
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(String fileName, String fileType,
                                           @RequestParam("fileBytes") MultipartFile file) {
        Assert.notBlank(fileName, "fileName cannot be blank");
        Assert.notBlank(fileType, "fileType cannot be blank");
        Assert.notNull(file, "file Bytes cannot be null");

        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFilename(fileName);
        fileMetadata.setFileType(fileType);
        String fileParentPathName = null;
        try {
            fileParentPathName = fileService.uploadFile(fileMetadata, file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(fileParentPathName);
    }
}
