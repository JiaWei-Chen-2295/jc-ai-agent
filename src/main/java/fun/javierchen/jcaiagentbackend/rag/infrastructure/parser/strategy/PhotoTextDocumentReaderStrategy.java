package fun.javierchen.jcaiagentbackend.rag.infrastructure.parser.strategy;

import fun.javierchen.jcaiagentbackend.rag.infrastructure.parser.PhotoTextContext;
import org.springframework.ai.document.Document;

import java.util.List;

public interface PhotoTextDocumentReaderStrategy {
    List<Document> read(PhotoTextContext context);
}
