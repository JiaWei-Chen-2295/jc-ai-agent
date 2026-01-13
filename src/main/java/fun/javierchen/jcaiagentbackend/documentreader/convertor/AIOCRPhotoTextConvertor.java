package fun.javierchen.jcaiagentbackend.documentreader.convertor;

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
import fun.javierchen.jcaiagentbackend.documentreader.PhotoTextContext;
import fun.javierchen.jcaiagentbackend.documentreader.PhotoType;
import fun.javierchen.jcaiagentbackend.utils.PhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.util.*;

@Slf4j
public class AIOCRPhotoTextConvertor implements PhotoTextConvertor {

    private static final String DEFAULT_PROMPT = """
            从图片中提取所有知识点信息，按照给定的JSON结构输出。
            
            要求：
            1. 识别图片中的所有知识点内容
            2. 每个知识点必须包含：标题、内容、标签、前置知识
            3. 确保输出完整的JSON，不要截断
            4. 如果图片内容较多，请全部提取，不要遗漏
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
        knowledgeItem.addProperty("知识点标题", "string");
        knowledgeItem.add("知识点标签", new JsonArray());
        knowledgeItem.addProperty("知识点内容", "string");
        knowledgeItem.addProperty("知识点前置知识", "string");

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
        String resultText = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
        
        log.info("OCR结果长度: {} 字符", resultText.length());
        log.info("finish_reason: {}", result.getOutput().getChoices().get(0).getFinishReason());
        
        return resultText;
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
