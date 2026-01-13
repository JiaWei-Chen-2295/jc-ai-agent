package fun.javierchen.jcaiagentbackend.rag.application.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档解析器工厂
 * 根据文件类型选择合适的解析器
 *
 * @author JavierChen
 */
@Component
@Slf4j
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserMap;
    private final DocumentParser defaultParser;

    public DocumentParserFactory(List<DocumentParser> parsers, TikaDocumentParser tikaDocumentParser) {
        this.defaultParser = tikaDocumentParser;

        // 构建类型到解析器的映射
        this.parserMap = parsers.stream()
                .flatMap(parser -> parser.supportedTypes().stream()
                        .map(type -> Map.entry(type.toLowerCase(), parser)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing // 保留第一个
                ));

        log.info("文档解析器工厂初始化完成，支持的类型: {}", parserMap.keySet());
    }

    /**
     * 根据文件类型获取解析器
     *
     * @param fileType 文件类型（如 pdf、docx）
     * @return 对应的解析器
     */
    public DocumentParser getParser(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            log.warn("文件类型为空，使用默认解析器");
            return defaultParser;
        }

        DocumentParser parser = parserMap.get(fileType.toLowerCase());
        if (parser == null) {
            log.warn("未找到文件类型 {} 的专用解析器，使用默认解析器", fileType);
            return defaultParser;
        }
        return parser;
    }

    /**
     * 根据文件名获取解析器
     *
     * @param filename 文件名
     * @return 对应的解析器
     */
    public DocumentParser getParserByFilename(String filename) {
        String fileType = extractFileType(filename);
        return getParser(fileType);
    }

    /**
     * 从文件名中提取文件类型
     */
    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 检查文件类型是否支持
     */
    public boolean isSupported(String fileType) {
        return parserMap.containsKey(fileType.toLowerCase());
    }
}
