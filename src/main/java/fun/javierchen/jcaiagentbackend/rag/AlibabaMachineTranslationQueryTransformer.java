package fun.javierchen.jcaiagentbackend.rag;

import com.aliyun.teaopenapi.models.OpenApiRequest;
import com.aliyun.teaopenapi.models.Params;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.HashMap;
import java.util.Map;

/**
 * 将查询进行机器翻译
 * 翻译的结果总是中文
 * 因为我们的知识库是中文的
 */
@Slf4j
public class AlibabaMachineTranslationQueryTransformer implements QueryTransformer {

    @Setter
    private String targetLanguage;
    private final com.aliyun.teaopenapi.Client client;

    public AlibabaMachineTranslationQueryTransformer(String targetLanguage, com.aliyun.teaopenapi.Client client) {
        this.targetLanguage = targetLanguage;
        this.client = client;
    }

    private Params getParams() {
        return new Params()
                // 接口名称
                .setAction("TranslateGeneral")
                // 接口版本
                .setVersion("2018-10-12")
                // 接口协议
                .setProtocol("HTTPS")
                // 接口 HTTP 方法
                .setMethod("POST")
                .setAuthType("AK")
                .setStyle("RPC")
                // 接口 PATH
                .setPathname("/")
                // 接口请求体内容格式
                .setReqBodyType("formData")
                // 接口响应体内容格式
                .setBodyType("json");
    }

    private Map<String, Object> buildTranslationRequest(String text) {
        return Map.of(
                "FormatType", "text",
                "SourceLanguage", "auto",
                "TargetLanguage", targetLanguage,
                "SourceText", text,
                "Scene", "general"
        );
    }



    @Override
    public Query transform(Query query) {
        String text = query.text();
        // body params
        Map<String, Object> body = buildTranslationRequest(text);
        // runtime options
        RuntimeOptions runtime = new RuntimeOptions();
        OpenApiRequest request = new OpenApiRequest()
                .setBody(body);
        // 返回值实际为 Map 类型，可从 Map 中获得三类数据：响应体 body、响应头 headers、HTTP 返回的状态码 statusCode。
        Map<String, ?> stringMap = null;
        try {
            stringMap = client.callApi(getParams(), request, runtime);
        } catch (Exception e) {
            log.error("Failed to translate text: {}", e.getMessage());
            return query;
        }
        Map<String, Object> bodyMap = (Map<String, Object>) stringMap.get("body");
        Map<String, Object> dataMap = (Map<String, Object>) bodyMap.get("Data");
        String translated = (String) dataMap.get("Translated");

        if (!stringMap.containsKey("body")) {
            return query;
        }

        log.info("Translated Text: {}", translated);

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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder{
        private String targetLanguage = "zh";
        private com.aliyun.teaopenapi.Client client;

        private Builder(){}

        public Builder targetLanguage(String targetLanguage){
            this.targetLanguage = targetLanguage;
            return this;
        }
        public Builder client(com.aliyun.teaopenapi.Client client){
            this.client = client;
            return this;
        }

        public AlibabaMachineTranslationQueryTransformer build(){
            return new AlibabaMachineTranslationQueryTransformer(this.targetLanguage, this.client);
        }
    }
}
