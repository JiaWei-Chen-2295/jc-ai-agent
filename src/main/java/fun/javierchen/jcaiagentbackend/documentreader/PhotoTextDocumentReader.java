package fun.javierchen.jcaiagentbackend.documentreader;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;

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
        PhotoTextDocumentReaderStrategy photoTextDocumentReaderStrategy;
        if (documentReaderStrategy.equals("json")) {
            photoTextDocumentReaderStrategy = new JSONPhotoTextDocumentReaderStrategy();
        } else {
            // 使用默认的策略
            photoTextDocumentReaderStrategy = new DefaultPhotoTextDocumentReaderStrategy();
        }
        return photoTextDocumentReaderStrategy.read(
                new PhotoTextContext(photosPath, PhotoType.HANDWRITE)
        );
    }
}
