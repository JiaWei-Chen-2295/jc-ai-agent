package fun.javierchen.jcaiagentbackend.documentreader;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.OcrOptions;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AIOCRPhotoTextConvertor implements PhotoTextConvertor {

    private static final String DEFAULT_PROMPT = """
            Please extract all recognizable textual content from the provided image and convert it into a well-structured Markdown document.
            
            Requirements:
            
            If the image contains a document without a clear title, analyze the content and generate a suitable title to be placed as an H1 heading (# Title).
            
            Preserve and organize paragraphs, bullet points, numbering, and other layout elements to closely match the original document structure.
            
            If there are tables or charts, convert them into Markdown table format where possible.
            
            Ignore irrelevant elements such as watermarks, page numbers, or decorative graphics.
            
            Output the result in pure Markdown format only, with no additional explanation or commentary.
            
            If multiple images or pages are provided, separate each section using ## Page X.
            """;
    private final String API_KEY = System.getenv("JC_AI_AGENT_API_KEY");

    @Override
    public String convert(PhotoTextContext context) {
        try {
            return parsePhoto(context);
        } catch (NoApiKeyException | UploadFileException e) {
            log.error("提取图片文字失败{}", e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
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
//        String stableDataUrlListMD5 = PhotoUtils.getStableDataUrlListMD5(context.photoUrlList());
//        if (PHOTO_TEXT_CACHE.containsKey(stableDataUrlListMD5)) {
//            log.info("命中缓存");
//            return PHOTO_TEXT_CACHE.get(stableDataUrlListMD5);
//        }

        MultiModalConversation conv = new MultiModalConversation();
        Map<String, Object> map = new HashMap<>();
        List<String> photoUrlList = context.photoUrlList();
        if (!photoUrlList.isEmpty()) {
            for (String url : photoUrlList) {
                map.put("image", url);
            }
        }

        JsonObject resultSchema = new JsonObject();
        JsonArray knowledgeArray = new JsonArray();

        JsonObject knowledgeItem = new JsonObject();
        knowledgeItem.addProperty("知识点标题", "");
        knowledgeItem.add("知识点标签", new JsonArray());
        knowledgeItem.addProperty("知识点内容", "");
        knowledgeItem.addProperty("知识点前置知识", "");

        knowledgeArray.add(knowledgeItem);
        resultSchema.add("知识点集合", knowledgeArray);


        // 配置内置的OCR任务
        OcrOptions ocrOptions = OcrOptions.builder()
                .task(OcrOptions.Task.KEY_INFORMATION_EXTRACTION)
                .taskConfig(OcrOptions.TaskConfig.builder()
                        .resultSchema(resultSchema)
                        .build())
                .build();

        // 输入图像的最大像素阈值，超过该值图像会按原比例缩小，直到总像素低于max_pixels
        map.put("max_pixels", "6422528");
        // 输入图像的最小像素阈值，小于该值图像会按原比例放大，直到总像素大于min_pixels
        map.put("min_pixels", "3136");
        // 开启图像自动转正功能
        map.put("enable_rotate", true);
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        map,
                        Collections.singletonMap("text", DEFAULT_PROMPT))).build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(API_KEY)
                .model("qwen-vl-ocr-latest")
                .ocrOptions(ocrOptions)
                .message(userMessage)
                .build();
        MultiModalConversationResult result = conv.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
    }

    public static void main(String[] args) throws IOException {
        ResourcePatternResolver resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(new FileSystemResourceLoader());
        Resource[] resources = resourcePatternResolver.getResources("classpath:/photo/*jpg");
        List<String> photoDataUrlList = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource.isFile()) {
                String photoDataUrl = PhotoUtils.convertImageToDataURL(resource.getFile());
                photoDataUrlList.add(photoDataUrl);
            }
        }
        PhotoTextContext photoTextContext = new PhotoTextContext(photoDataUrlList, PhotoType.HANDWRITE);
        PhotoTextConvertor converter = new AIOCRPhotoTextConvertor();
        String result = converter.convert(photoTextContext);
        System.out.println(result);
    }
}
