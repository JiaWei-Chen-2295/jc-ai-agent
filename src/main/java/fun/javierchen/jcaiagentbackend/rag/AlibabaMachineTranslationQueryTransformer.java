package fun.javierchen.jcaiagentbackend.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.Map;

/**
 * 将查询进行机器翻译
 * 翻译的结果总是中文
 * 因为我们的知识库是中文的
 */
@Slf4j
public class AlibabaMachineTranslationQueryTransformer implements QueryTransformer {

    @Resource
    private com.aliyun.teaopenapi.Client client;

    @Resource
    private com.aliyun.teaopenapi.models.Params params;

    @Override
    public Query transform(Query query) {

        String text = query.text();

        // body params
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("FormatType", "text");
        body.put("SourceLanguage", "en");
        body.put("TargetLanguage", "zh");
        body.put("SourceText", text);
        body.put("Scene", "general");
        // runtime options
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        com.aliyun.teaopenapi.models.OpenApiRequest request = new com.aliyun.teaopenapi.models.OpenApiRequest()
                .setBody(body);
        // 复制代码运行请自行打印 API 的返回值
        // 返回值实际为 Map 类型，可从 Map 中获得三类数据：响应体 body、响应头 headers、HTTP 返回的状态码 statusCode。
        Map<String, ?> stringMap = null;
        try {
            stringMap = client.callApi(params, request, runtime);
        } catch (Exception e) {
            log.error("Failed to translate text: " + e.getMessage());
        }
        Map<String, Object> bodyMap = (Map<String, Object>) stringMap.get("body");
        Map<String, Object> dataMap = (Map<String, Object>) bodyMap.get("Data");
        String translated = (String) dataMap.get("Translated");
        log.info("Translated Text: " + translated);
        return Query.builder()
                .text(translated)
                .context(query.context())
                .history(query.history())
                .build();
    }

    @Override
    public Query apply(Query query) {
        return QueryTransformer.super.apply(query);
    }
}
