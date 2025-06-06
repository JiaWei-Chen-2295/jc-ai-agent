package fun.javierchen.jcaiagentbackend.documentreader;

import org.springframework.ai.document.Document;

import java.util.List;

public interface PhotoTextDocumentReaderStrategy {
    List<Document> read(PhotoTextContext context);
}
