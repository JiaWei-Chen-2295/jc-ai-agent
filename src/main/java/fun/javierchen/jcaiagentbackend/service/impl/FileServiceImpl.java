package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.model.FileMetadata;
import fun.javierchen.jcaiagentbackend.service.FileService;
import fun.javierchen.jcaiagentbackend.utils.FileUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    @Override
    public String uploadFile(FileMetadata fileMetadata, byte[] fileBytes) throws IOException {
        String fileId = UUID.randomUUID().toString();
        Path uploadFilePath = Paths.get("upload_file", fileId);
        // 保存文件到服务器中
        FileUtils.byteToFile(fileBytes, uploadFilePath.toString(), fileMetadata.getFilename());
        return fileId;
    }
}