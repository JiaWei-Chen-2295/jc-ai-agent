package fun.javierchen.jcaiagentbackend.rag;


import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Love App 文档加载器
 *
 * @author JavierChen
 * @date 2025/05/24
 */
@Component
public class LoveAppDocumentLoader {

    private ResourcePatternResolver resourcePatternResolver;

    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 Markdown 文档
     *
     * @return
     * @throws IOException
     */
    public List<Document> loadMarkDowns() throws IOException {
        List<Document> documentList = new ArrayList<>();
        Resource[] resources = resourcePatternResolver.getResources("classpath:doc/*.md");
        // 配置 DocumentReaderConfig
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeBlockquote(false)
                    .withIncludeCodeBlock(false)
                    .withAdditionalMetadata("filename", filename)
                    .build();
            // 创建 DocumentReader
            MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
            List<Document> documents = markdownDocumentReader.get();
            documentList.addAll(documents);
        }
        return documentList;
    }
}
