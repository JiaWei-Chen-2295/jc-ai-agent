package fun.javierchen.jcaiagentbackend.translation;

import com.aliyun.tea.*;

import java.util.Map;

public class AliTranslationTest {


    /**
     * <b>description</b> :
     * <p>使用凭据初始化账号Client</p>
     *
     * @return Client
     * @throws Exception
     */
    public static com.aliyun.teaopenapi.Client createClient() throws Exception {
        // 工程代码建议使用更安全的无AK方式，凭据配置方式请参见：https://help.aliyun.com/document_detail/378657.html。
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential);
        // Endpoint 请参考 https://api.aliyun.com/product/alimt
        config.endpoint = "mt.cn-hangzhou.aliyuncs.com";
        return new com.aliyun.teaopenapi.Client(config);
    }

    /**
     * <b>description</b> :
     * <p>API 相关</p>
     *
     * @param string Path parameters
     * @return OpenApi.Params
     */
    public static com.aliyun.teaopenapi.models.Params createApiInfo() throws Exception {
        com.aliyun.teaopenapi.models.Params params = new com.aliyun.teaopenapi.models.Params()
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
        return params;
    }

    public static void main(String[] args_) throws Exception {
        com.aliyun.teaopenapi.Client client = AliTranslationTest.createClient();
        com.aliyun.teaopenapi.models.Params params = AliTranslationTest.createApiInfo();
        // body params
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("FormatType", "text");
        body.put("SourceLanguage", "en");
        body.put("TargetLanguage", "zh");
        body.put("SourceText", "Hello, I am currently struggling to maintain a relationship after marriage. Please give me some advice");
        body.put("Scene", "general");
        // runtime options
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        com.aliyun.teaopenapi.models.OpenApiRequest request = new com.aliyun.teaopenapi.models.OpenApiRequest()
                .setBody(body);
        // 复制代码运行请自行打印 API 的返回值
        // 返回值实际为 Map 类型，可从 Map 中获得三类数据：响应体 body、响应头 headers、HTTP 返回的状态码 statusCode。
        Map<String, ?> stringMap = client.callApi(params, request, runtime);
        Map<String, Object> bodyMap = (Map<String, Object>) stringMap.get("body");
        Map<String, Object> dataMap = (Map<String, Object>) bodyMap.get("Data");
        String translated = (String) dataMap.get("Translated");
        System.out.println("Translated Text: " + translated);

    }


}
