package fun.javierchen.jcaiagentbackend.rag.application.ingestion.parser;

import fun.javierchen.jcaiagentbackend.documentreader.PhotoTextDocumentReader;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Image parser backed by documentreader utilities.
 */
@Component
@Slf4j
public class ImageDocumentParser implements DocumentParser {

    private static final String DEFAULT_STRATEGY = "json";

    @Override
    public List<Document> parse(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("file not found: " + filePath);
        }

        String dataUrl = PhotoUtils.convertImageToDataURL(file);
        PhotoTextDocumentReader reader = new PhotoTextDocumentReader(List.of(dataUrl), DEFAULT_STRATEGY);
        List<Document> documents = reader.get();

        if (documents == null || documents.isEmpty()) {
            log.warn("image parse result empty: {}", filePath);
            return List.of();
        }

        String extension = getFileExtension(file.getName());
        documents.forEach(doc -> {
            doc.getMetadata().put("source", filePath);
            doc.getMetadata().put("filename", file.getName());
            doc.getMetadata().put("fileType", extension);
        });

        log.info("parsed image for RAG: file={}, chunks={}", file.getName(), documents.size());
        return documents;
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("jpg", "jpeg", "png", "gif");
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }
}
