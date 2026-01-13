package fun.javierchen.jcaiagentbackend.rag.application.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用文档解析器
 * 使用 Apache Tika 提取文本，支持 PDF、Word、PPT 等多种格式
 *
 * @author JavierChen
 */
@Component
@Slf4j
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 文本分割器配置
     * - defaultChunkSize: 800 tokens（适合 embedding 模型）
     * - minChunkSizeChars: 350 字符最小块
     * - minChunkLengthToEmbed: 5 字符以上才会被 embed
     * - maxNumChunks: 10000 最大分块数
     */
    private final TextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    @Override
    public List<Document> parse(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        log.info("开始解析文档: {}", filePath);

        // 使用 Tika 提取文本
        String content = extractText(file);

        if (content == null || content.isBlank()) {
            log.warn("文档内容为空: {}", filePath);
            return List.of();
        }

        // 构建元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filePath);
        metadata.put("filename", file.getName());
        metadata.put("fileType", getFileExtension(file.getName()));

        // 创建原始文档
        Document rawDocument = new Document(content, metadata);

        // 分割文档
        List<Document> chunks = textSplitter.split(rawDocument);

        log.info("文档解析完成: {}, 分块数量: {}", filePath, chunks.size());
        return chunks;
    }

    /**
     * 使用 Tika 提取文本
     */
    private String extractText(File file) throws IOException, TikaException {
        return tika.parseToString(file);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("pdf", "doc", "docx", "ppt", "pptx", "txt", "md", "html");
    }
}
