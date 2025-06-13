package fun.javierchen.jcaiagentbackend.documentreader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class JSONPhotoTextDocumentReaderStrategy implements PhotoTextDocumentReaderStrategy {

    /**
     * 图片提取处文字的缓存
     * key: Photo 文件名
     * value: 图片对应的文字
     */
    private static final Map<String, String> PHOTO_TEXT_CACHE = Map.of("fc8eae09622c1315f978dfa24e2fd2b1", "7af9a350-b3fd-41a5-9d54-5513b0d5d404");



    @Override
    public List<Document> read(PhotoTextContext context) {
        String photoId = null;
        List<Document> documents = null;
        try {
            PhotoTextConvertor photoTextConvertor = new AIOCRPhotoTextConvertor();
            String rawText = photoTextConvertor.convert(context);

            // 去除代码块标记
            String jsonStr = rawText.replaceFirst("^```json\\s*", "")
                                   .replaceFirst("\\s*```$", "");

            // 解析整个JSON对象
            JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();

            // 提取知识点集合数组
            JsonArray knowledgeArray = jsonObject.getAsJsonArray("知识点集合");
            String cleanJson = knowledgeArray.toString();

            log.info("知识点集合JSON数组: {}", cleanJson);

            ByteArrayResource resource = new ByteArrayResource(cleanJson.getBytes());
            JsonReader jsonReader = new JsonReader(
                resource,
                "知识点内容",
                "知识点标题", "知识点标签", "知识点前置知识"
            );
            documents =  jsonReader.get();
        } catch (Exception e) {
            log.error("解析图片失败{}", e.getMessage());
            throw new RuntimeException(e);
        }
        // 使用 Spring AI
        return documents;
    }


    public static void main(String[] args) throws IOException {
        ResourcePatternResolver resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(new FileSystemResourceLoader());
        Resource[] resources = resourcePatternResolver.getResources("classpath:/photo/*jpg");
        List<String> photoDataUrlList = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource.isFile()) {
                String photoDataUrl = PhotoUtils.convertImageToDataURL(resource.getFile());
//                String imageMD5 = PhotoUtils.getImageMD5(resource.getFile());
//                System.out.println(photoDataUrl);
                photoDataUrlList.add(photoDataUrl);
            }
        }
        PhotoTextContext photoTextContext = new PhotoTextContext(photoDataUrlList, PhotoType.HANDWRITE);
        String stableDataUrlListMD5 = PhotoUtils.getStableDataUrlListMD5(photoTextContext.photoUrlList());
        System.out.println(stableDataUrlListMD5);
//        new DefaultPhotoTextDocumentReaderStrategy().read(photoTextContext);
        List<Document> read = new JSONPhotoTextDocumentReaderStrategy().read(photoTextContext);

    }


}
