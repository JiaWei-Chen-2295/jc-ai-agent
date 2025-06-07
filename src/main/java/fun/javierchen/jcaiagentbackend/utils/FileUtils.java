package fun.javierchen.jcaiagentbackend.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {


    /**
     * 保存字节流到指定目录
     *
     * @param bytes      文件字节数组
     * @param folderName 目标文件夹名称（存放于项目根目录）
     * @param fileName   文件名（包含扩展名）
     * @return 生成的文件对象
     * @throws IOException 当目录创建失败或文件写入失败时抛出
     */
    public static File byteToFile(byte[] bytes, String folderName, String fileName) throws IOException {
        // 校验参数
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File bytes cannot be empty");
        }

        // 构建目标目录路径
        File targetDir = new File(System.getProperty("user.dir"), folderName);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
        }

        // 构建目标文件
        File targetFile = new File(targetDir, fileName);

        // 写入文件（使用try-with-resources自动关闭流）
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(bytes);
            fos.flush();
        }

        return targetFile;
    }
}


