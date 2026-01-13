package fun.javierchen.jcaiagentbackend.pptreader;


import fun.javierchen.jcaiagentbackend.pptreader.PhotoEmbeddedDocumentExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

public class PPTReader {

    @Test
    public void readPPT() throws IOException, SAXException, TikaException {
        ResourcePatternResolver resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(new FileSystemResourceLoader());
        Resource[] resources = resourcePatternResolver.getResources("classpath:/doc/*.ppt*");
        if (resources.length == 0) {
            System.out.println("未找到PPT文件");
            return;
        }
        
        // 设置解析上下文，使用自定义的图片提取器
        ParseContext context = new ParseContext();
        // 如果需要提取图片，取消注释PhotoEmbeddedDocumentExtractor并使用它
        // context.set(EmbeddedDocumentExtractor.class, new PhotoEmbeddedDocumentExtractor());

        // 使用BodyContentHandler提取纯文本内容，-1表示不限制字符数
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        
        try (InputStream inputStream = resources[0].getInputStream()) {
            System.out.println("正在解析文件: " + resources[0].getFilename());
            
            parser.parse(inputStream, handler, metadata, context);
            
            // 输出提取的文本内容
            String content = handler.toString();
            System.out.println("\n===== 提取的文本内容 =====");
            System.out.println(content);
            
            // 输出元数据信息
            System.out.println("\n===== 文档元数据 =====");
            for (String name : metadata.names()) {
                System.out.println(name + ": " + metadata.get(name));
            }
            
            // 输出内容统计
            System.out.println("\n===== 统计信息 =====");
            System.out.println("提取的字符数: " + content.length());
            System.out.println("提取的行数: " + content.split("\\n").length);
            
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
