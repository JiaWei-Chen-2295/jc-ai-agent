package fun.javierchen.jcaiagentbackend.rag.study;


import fun.javierchen.jcaiagentbackend.documentreader.PhotoTextDocumentReader;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  学习助手 文档加载器
 *
 * @author JavierChen
 * @date 2025/05/24
 */
@Component
public class StudyFriendDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public StudyFriendDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 图片中的 文档
     *
     * @return
     * @throws IOException
     */
    public List<Document> loadStudyPhotos() throws IOException {
        List<Document> documentList = null;
        Resource[] resources = resourcePatternResolver.getResources("classpath:/photo/*jpg");
        List<String> photoDataUrlList = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource.isFile()) {
                String photoDataUrl = PhotoUtils.convertImageToDataURL(resource.getFile());
                photoDataUrlList.add(photoDataUrl);
            }
        }
        PhotoTextDocumentReader reader = new PhotoTextDocumentReader(photoDataUrlList, "json");
        documentList = reader.get();
        return documentList;
    }
}
