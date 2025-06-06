package fun.javierchen.jcaiagentbackend.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class PhotoUtils {

    private PhotoUtils(){}

    /**
     * 将图片 File 对象转换为 Base64 编码的 data url
     * @param imageFile
     * @return
     * @throws IOException
     */
    public static String convertImageToDataURL(File imageFile) throws IOException {
        String fileExtension = getFileExtension(imageFile);
        String mimeType = getMimeType(fileExtension);
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        return "data:" + mimeType + ";base64," + base64Image;
    }

    private static String getFileExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    private static String getMimeType(String extension) {
        switch (extension.toLowerCase()) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            default: return "image/" + extension.toLowerCase();
        }
    }


    /**
     * 计算图片文件的 MD5 哈希值
     * @param imageFile 图片文件对象
     * @return 32位小写MD5字符串
     * @throws IOException 文件读取异常
     */
    public static String getImageMD5(File imageFile) throws IOException {
        try {
            byte[] fileBytes = Files.readAllBytes(imageFile.toPath());
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(fileBytes);

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 理论上不会发生，MD5 是标准算法
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 计算图片集合的稳定MD5（顺序无关，仅校验内容存在性）
     * @param imageFiles 图片文件列表
     * @return 32位小写MD5字符串
     * @throws IOException 文件读取异常
     */
    public static String getStableImagesMD5(List<File> imageFiles) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 对每个文件的MD5进行排序后合并
            List<String> sortedMD5s = imageFiles.stream()
                    .sorted((f1, f2) -> {
                        try {
                            return getImageMD5(f1).compareTo(getImageMD5(f2));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(file -> {
                        try {
                            return getImageMD5(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            // 合并所有MD5值
            for (String singleMD5 : sortedMD5s) {
                md.update(singleMD5.getBytes());
            }

            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }


    /**
     * 计算 Base64 data URL 列表的稳定 MD5（顺序无关）
     * @param dataUrls data URL 列表
     * @return 32位小写MD5字符串
     */
    public static String getStableDataUrlListMD5(List<String> dataUrls) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 提取并排序每个 data URL 的 MD5
            List<String> sortedMD5s = dataUrls.stream()
                    .map(PhotoUtils::parseDataUrlToMD5)
                    .sorted()
                    .collect(Collectors.toList());

            // 合并所有 MD5 值
            for (String singleMD5 : sortedMD5s) {
                md.update(singleMD5.getBytes());
            }

            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 解析 data URL 并计算 MD5
     * @param dataUrl 完整的 data URL 字符串
     * @return 图片内容的 MD5
     */
    private static String parseDataUrlToMD5(String dataUrl) {
        try {
            byte[] imageBytes = parseDataUrl(dataUrl);
            return calculateBytesMD5(imageBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data URL: " + dataUrl, e);
        }
    }

    /**
     * 从 data URL 提取图片字节数据
     */
    private static byte[] parseDataUrl(String dataUrl) {
        String[] parts = dataUrl.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid data URL format");
        }
        return Base64.getDecoder().decode(parts[1]);
    }

    /**
     * 计算字节数组的 MD5
     */
    private static String calculateBytesMD5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }




    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


}
