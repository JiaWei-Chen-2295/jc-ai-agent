package fun.javierchen.jcaiagentbackend.pptreader;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

public class PhotoEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    private static final AtomicInteger imageCounter = new AtomicInteger(1);
    private final Path outputDir = Path.of(System.getProperty("user.dir"), "output");

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        // 处理所有文档的嵌入的图片
        return true;
    }

    @Override
    public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {
        // 检查是否为图片类型
        String mimeType = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith("image/")) {
            // 创建输出目录（如果不存在）
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // 生成唯一文件名
            String extension = mimeType.split("/")[1];
            String fileName = "image_" + imageCounter.getAndIncrement() + "." + extension;
            Path imagePath = outputDir.resolve(fileName);

            // 保存图片到文件系统
            try (InputStream in = TikaInputStream.get(inputStream)) {
                Files.copy(in, imagePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Extracted image: " + imagePath);
            }
        }
    }
}
