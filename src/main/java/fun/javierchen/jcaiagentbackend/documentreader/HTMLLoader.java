package fun.javierchen.jcaiagentbackend.documentreader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HTMLLoader {

    public static List<Document> load(String photoTextId) throws IOException {
        List<Document> documentsList = new ArrayList<>();
        String rootPath = System.getProperty("user.dir");
        Path photoHtmlPath = Paths.get(rootPath, "photo_html", photoTextId);
        ResourcePatternResolver resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(new FileSystemResourceLoader());
        Resource[] resources = resourcePatternResolver.getResources(photoHtmlPath.toString());
        for (Resource resource : resources) {
            JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                    .charset("UTF-8")  // 修复中文乱码
                    .selector("section") // 正确方法名
                    .includeLinkUrls(true)
                    .build();
            JsoupDocumentReader reader = new JsoupDocumentReader(resource, config);
            List<Document> documentList = reader.get();
            documentsList.addAll(documentList);
        }
        return documentsList;
    }

    public static void main(String[] args) throws IOException {
        List<Document> documentList = load("3183914d-e51e-455c-9999-cbc255a4a706");
        System.out.println(documentList);
    }

}
