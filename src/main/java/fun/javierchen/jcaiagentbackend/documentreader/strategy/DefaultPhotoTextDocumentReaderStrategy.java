package fun.javierchen.jcaiagentbackend.documentreader.strategy;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import fun.javierchen.jcaiagentbackend.documentreader.textloader.HTMLLoader;
import fun.javierchen.jcaiagentbackend.documentreader.PhotoTextContext;
import fun.javierchen.jcaiagentbackend.documentreader.PhotoType;
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
    private static final Map<String, String> PHOTO_TEXT_CACHE = Map.of("fc8eae09622c1315f978dfa24e2fd2b1", "7af9a350-b3fd-41a5-9d54-5513b0d5d404");

    private static final String DEFAULT_PROMPT = """

                                                       **Role Definition**

                                                       text
                                                       You are a professional HTML/SVG conversion specialist. Analyze the provided image and convert its visual elements into semantic HTML structures following these guidelines:
                                                       ```
            
                                                       ## Core Requirements
                                                       1. **Visual Fidelity**
                                                          - Preserve original layout hierarchy
                                                          - Maintain color schemes and spatial relationships
                                                          - Retain critical textual details
            
                                                       2. **SVG Optimization**
                                                       ```HTML
                                                       <!-- Bad Practice -->
                                                       <img src="diagram.jpg">
                                                       <!-- Good Practice -->
                                                       <svg viewBox="0 0 800 600">\s
                                                         <rect x="50" y="50" width="100" height="50"/>\s
                                                         <text x="75" y="75">Layer</text>\s
                                                       </svg>
                                                       ```
            
                                                       3. **Responsive Design**
                                                          - Use percentage-based dimensions
                                                          - Implement media queries for mobile
                                                          - Maintain aspect ratios
            
                                                       ## Structural Specifications
            
                                                       ### Hierarchy Rules
                                                       ```HTML
                                                       <section class="knowledge-group">\s
                                                         <h3 class="group-title">Network Protocol Stack</h3>\s
                                                         <!-- split-point -->\s
                                                         <div class="knowledge-point">\s
                                                           <p class="text-chunk">Encapsulation process:</p>\s
                                                           <ul class="structured-list">\s
                                                             <li>Application Layer → Data</li>\s
                                                             <li>Transport Layer → Segments</li>\s
                                                           </ul>\s
                                                         </div>\s
                                                       </section>
                                                       ```
            
                                                       ### Semantic Markup
            
                                                       | Element Type       | HTML Tag         | CSS Class               |
                                                       |--------------------|------------------|-------------------------|
                                                       | Conceptual Group   | `<section>`      | `.knowledge-group`      |
                                                       | Individual Concept | `<div>`          | `.knowledge-point`      |
                                                       | Technical Terms    | `<span>`         | `.key-term`             |
                                                       | Mathematical Formulas | `<div>`       | `.formula-block`        |
            
                                                       ## Segmentation Rules
            
                                                       1. **Logical Division**
                                                          Use ▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃ to divide sections
                                                       ```HTML
                                                       [Category Tag] Sample Title
                                                       ▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃
                                                       Content block...
                                                       ▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃
                                                       ```
            
                                                       2. **Visual Separation**
                                                       ```HTML
                                                       <div class="diagram-container">\s
                                                         <svg>...</svg>\s
                                                         <p class="diagram-caption">Figure 1: Protocol Stack</p>\s
                                                       </div>\s
                                                       <!-- split-point -->
                                                       ```
            
                                                       ## Output Validation
            
                                                       1. **Code Quality Checklist**
                                                          - [ ] No redundant nested elements
                                                          - [ ] All SVG elements have proper viewBox
                                                          - [ ] Semantic HTML5 tags used appropriately
                                                          - [ ] Responsive breakpoints defined
            
                                                       2. **Prohibited Patterns**
                                                          - [ ] Inline styles without class abstraction
                                                          - [ ] Use of deprecated HTML elements
                                                          - [ ] Hardcoded pixel dimensions without fallbacks
            
                                                       ## Quality Assurance Checklist
                                                          - [ ] All content blocks have a category tag
                                                          - [ ] Divider length ≥ 20 underscores (▃)
                                                          - [ ] Key terms wrapped in *asterisks*
                                                          - [ ] No raw HTML tags outside designated code blocks
            
                                                       ## Example Output
            
                                                       ## Compliance Requirements
                                                       1. Strictly return **ONLY** HTML code and ▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃ dividers
                                                       2. Exclude any natural language explanations
                                                       3. Prioritize structural accuracy over visual perfection
                                                       4. Maintain W3C validation standards
                                                       ```
            
                                                       Let me know if you'd like this turned into a reusable template or formatted for a specific AI interface (like ChatGPT, Claude, etc.).
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
