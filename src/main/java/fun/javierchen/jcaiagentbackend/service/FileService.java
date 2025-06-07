package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.model.FileMetadata;

public interface FileService {

    /**
     * 上传文件 回传文件的标识 id
     * @param fileBytes
     * @return 文件的标识 ID -> 保存文件名
     */
    String uploadFile(FileMetadata fileMetadata, byte[] fileBytes);

}
