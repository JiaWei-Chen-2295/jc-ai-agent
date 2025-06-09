package fun.javierchen.jcaiagentbackend.tools;

import cn.hutool.http.HttpUtil;
import fun.javierchen.jcaiagentbackend.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

public class ResourceDownloadTool {

    // 固定下载目录（可根据需要修改）
    private static final String DOWNLOAD_DIR = FileConstant.FILE_BASE_PATH + File.separator + "downloads/";

    @Tool(description = "Download file from URL to fixed directory with specified filename")
    public String downloadFile(
        @ToolParam(description = "The source URL to download from") String url,
        @ToolParam(description = "Filename to save as") String fileName) {

        try {
            // 校验文件名有效性
            if (!fileName.matches("[a-zA-Z0-9_\\-\\\\.]+")) {
                return "Invalid filename. Only letters, numbers, -, _ and . are allowed";
            }

            // 构建完整保存路径
            String savePath = DOWNLOAD_DIR + fileName;
            File targetFile = new File(savePath);

            // 创建下载目录（如果不存在）
            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            // 执行下载
            long size = HttpUtil.downloadFile(url, targetFile);

            return String.format("File downloaded to: %s (Size: %s bytes)",
                              savePath, size);
        } catch (Exception e) {
            return "Download failed: " + e.getLocalizedMessage();
        }
    }
}
