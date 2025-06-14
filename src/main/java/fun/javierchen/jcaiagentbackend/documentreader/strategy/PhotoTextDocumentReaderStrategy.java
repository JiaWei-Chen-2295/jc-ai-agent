package fun.javierchen.jcaiagentbackend.documentreader.strategy;

import fun.javierchen.jcaiagentbackend.documentreader.PhotoTextContext;
import org.springframework.ai.document.Document;

import java.util.List;

public interface PhotoTextDocumentReaderStrategy {
    List<Document> read(PhotoTextContext context);
}
