package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.model.FileMetadata;
import fun.javierchen.jcaiagentbackend.service.FileService;
import fun.javierchen.jcaiagentbackend.utils.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    @Override
    public String uploadFile(FileMetadata fileMetadata, byte[] fileBytes) {
        String fileId = UUID.randomUUID().toString();
        Path uploadFilePath = Paths.get("upload_file", fileId);
        // 将文件保存到服务器中
        try {
            // 保存文件到服务器中
            File file = FileUtils.byteToFile(fileBytes, uploadFilePath.toString(), fileMetadata.getFilename());
            if (file != null) {
                return fileId;
            }
        } catch (Exception e) {
            // 处理保存文件失败的异常
            return null;
        }
        return null;
    }
}
