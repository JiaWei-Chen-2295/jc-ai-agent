package fun.javierchen.jcaiagentbackend.documentreader;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DefaultPhotoTextDocumentReaderStrategy implements PhotoTextDocumentReaderStrategy {

    /**
     * 图片提取处文字的缓存
     * key: Photo 文件名
     * value: 图片对应的文字
     */
    private static final Map<String, String> PHOTO_TEXT_CACHE = Map.of("fc8eae09622c1315f978dfa24e2fd2b1", "077711d4-1935-4cff-bc1e-4adb121c3cde");

    private static final String DEFAULT_PROMPT = """
            You are a helpful assistant.
            You are reading a photo and you need to extract the text and sub photo from the photo.
            Analyze the provided image and convert its visual elements (e.g., icons, charts, graphics) into HTML structures , prioritizing SVG vector graphics over raster images. Requirements:
            Preserve Layout : Retain key details like text, colors, and spatial arrangements.
            SVG Preference : Replace scalable components (lines, shapes, icons) with SVG code; avoid <img> tags.
            Responsive Design : Ensure the HTML adapts to different screen sizes.
            Clean Code : Output structured, semantic HTML/SVG with minimal redundancy; add comments for clarity.
            Logical Annotations : If the image contains complex logic (e.g., flowcharts, diagrams), provide a brief textual explanation or pseudocode alongside the code\s
            (https://github.com/jxzzlfh/awesome-stars )\s
            (https://roookie.space/ )."
            Example Output Format:
            ```html
                <!-- Extracted from image: SVG-based HTML structure -->
            <div class="diagram-container">
              <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
                <!-- Example SVG elements -->
                <circle cx="100" cy="100" r="80" fill="#FFD700" stroke="#333" stroke-width="4"/>
                <text x="50%" y="50%" text-anchor="middle" dominant-baseline="central" font-size="16" fill="#000">Sample</text>
              </svg>
            </div>
            ```
            Notes:
            For photos or complex textures , the model may default to <img> tags, but SVG will dominate for clean graphics.
            Provide high-resolution images to maximize recognition accuracy.
            You should only return QwenVL HTML from the photo.
            You should only return QwenVL HTML from the photo.
            You should only return QwenVL HTML from the photo.
             Segmentation Rules:
                - Create separate <section> for each logical unit (paragraph/diagram/formula)
                - Use <!-- split-point --> comments between unrelated elements
                - Wrap text clusters in <p class='text-chunk'> tags
             Segmentation Rules:
                - Create separate <section> for each logical unit (paragraph/diagram/formula)
                - Use <!-- split-point --> comments between unrelated elements
                - Wrap text clusters in <p class='text-chunk'> tags
             Segmentation Rules:
                - Create separate <section> for each logical unit (paragraph/diagram/formula)
                - Use <!-- split-point --> comments between unrelated elements
                - Wrap text clusters in <p class='text-chunk'> tags
            
            
            """;

    @Override
    public List<Document> read(PhotoTextContext context) {
        String photoId = null;
        List<Document> documents = null;
        try {
            photoId = parsePhoto(context);
            documents = HTMLLoader.load(photoId);
        } catch (Exception e) {
            log.error("解析图片失败{}", e.getMessage());
            throw new RuntimeException(e);
        }
        // 使用 Spring AI
        return documents;
    }

    /**
     * 使用 DashScope API 进行图片解析
     *
     * @param context
     * @throws NoApiKeyException
     * @throws UploadFileException
     */
    private String parsePhoto(PhotoTextContext context) throws NoApiKeyException, UploadFileException {

        // 检查缓存
        String stableDataUrlListMD5 = PhotoUtils.getStableDataUrlListMD5(context.photoUrlList());
        if (PHOTO_TEXT_CACHE.containsKey(stableDataUrlListMD5)) {
            log.info("命中缓存");
            return PHOTO_TEXT_CACHE.get(stableDataUrlListMD5);
        }

        MultiModalConversation conv = new MultiModalConversation();
        List<Map<String, Object>> userMessageContentMapList = context.photoUrlList().stream()
                .map(url -> Map.of("image", (Object) url))
                .collect(Collectors.toList());
        userMessageContentMapList.add(Map.of("text", context.photoType().getTypeString()));
        MultiModalMessage systemMessage = MultiModalMessage.builder().role(Role.SYSTEM.getValue())
                .content(List.of(
                        Collections.singletonMap("text", DEFAULT_PROMPT))).build();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(userMessageContentMapList).build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(System.getenv("JC_AI_AGENT_API_KEY"))
                .model("qwen-vl-max-latest")
                .messages(Arrays.asList(systemMessage, userMessage))
                .build();
        MultiModalConversationResult result = conv.call(param);
        String photoTextResult = result.getOutput().getChoices().getFirst().getMessage().getContent().getFirst().get("text").toString();
        log.info("图片的内容为:{}",photoTextResult);
        // 把结果保存为 HTML 文件保存
        String photoId = UUID.randomUUID().toString();
        try {
            savePhotoText(photoTextResult, photoId);
        } catch (IOException e) {
            log.error("保存图片失败{}", e.getMessage());
        }
        return photoId;
    }

    private void savePhotoText(String photoText, String photoTextId) throws IOException {
        // 获取项目根路径
        String projectRoot = System.getProperty("user.dir");
        Path targetPath = Paths.get(projectRoot,"photo_html", photoTextId);

        // 确保父目录存在
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }

        // 写入文件内容
        Files.write(targetPath, photoText.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
    }


}
