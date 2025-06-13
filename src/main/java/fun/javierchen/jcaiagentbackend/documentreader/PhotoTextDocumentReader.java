package fun.javierchen.jcaiagentbackend.documentreader;

import lombok.AllArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;

@AllArgsConstructor
public class PhotoTextDocumentReader implements DocumentReader {

    private List<String> photosPath;

    @Override
    public List<Document> get() {
       // 先使用默认的策略
//        return new DefaultPhotoTextDocumentReaderStrategy().read(
//                new PhotoTextContext(photosPath, PhotoType.HANDWRITE)
//        );

        // 使用 JSON 的策略
        return new JSONPhotoTextDocumentReaderStrategy().read(
                new PhotoTextContext(photosPath, PhotoType.HANDWRITE)
        );
    }
}
