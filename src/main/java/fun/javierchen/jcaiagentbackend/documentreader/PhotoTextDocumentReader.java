package fun.javierchen.jcaiagentbackend.documentreader;

import fun.javierchen.jcaiagentbackend.documentreader.strategy.DefaultPhotoTextDocumentReaderStrategy;
import fun.javierchen.jcaiagentbackend.documentreader.strategy.JSONPhotoTextDocumentReaderStrategy;
import fun.javierchen.jcaiagentbackend.documentreader.strategy.PhotoTextDocumentReaderStrategy;
import fun.javierchen.jcaiagentbackend.documentreader.strategy.PhotoTextDocumentReaderStrategyFactory;
import lombok.AllArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;

@AllArgsConstructor
public class PhotoTextDocumentReader implements DocumentReader {

    private List<String> photosPath;

    private final String documentReaderStrategy;

    public PhotoTextDocumentReader(List<String> photosPath) {
        this.photosPath = photosPath;
        this.documentReaderStrategy = "default";
    }


    @Override
    public List<Document> get() {
        PhotoTextDocumentReaderStrategy photoTextDocumentReaderStrategy = PhotoTextDocumentReaderStrategyFactory
                .getStrategy(documentReaderStrategy);
        return photoTextDocumentReaderStrategy.read(
                new PhotoTextContext(photosPath, PhotoType.HANDWRITE)
        );
    }
}
