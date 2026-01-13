package fun.javierchen.jcaiagentbackend.service.indexer;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档解析器接口
 * 支持多种文档格式解析为 Spring AI Document
 *
 * @author JavierChen
 */
public interface DocumentParser {

    /**
     * 解析文档文件
     *
     * @param filePath 文件路径
     * @return 解析后的文档列表（分块）
     * @throws Exception 解析异常
     */
    List<Document> parse(String filePath) throws Exception;

    /**
     * 支持的文件类型
     *
     * @return 文件类型列表
     */
    List<String> supportedTypes();
}
