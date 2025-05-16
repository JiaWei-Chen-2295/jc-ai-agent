package fun.javierchen.jcaiagentbackend.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public class HTTPInvoke {
    public static void main(String[] args) {
        String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        // 构造请求头
        String apiKey = System.getenv("JC_AI_AGENT_API_KEY"); // 从环境变量中获取 API Key
        JSONArray messages = JSONUtil.createArray();
        messages.add(JSONUtil.createObj()
                .put("role", "system")
                .put("content", "You are a helpful assistant."));
        messages.add(JSONUtil.createObj()
                .put("role", "user")
                .put("content", "你是谁？"));

        JSONObject requestBody = JSONUtil.createObj()
                .put("model", "qwen-plus")
                .put("messages", messages);

        // 发送请求
        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey) // 设置 Authorization 头
                .header("Content-Type", "application/json") // 设置 Content-Type 头
                .body(requestBody.toString()) // 设置请求体
                .execute();

        // 输出响应结果
        System.out.println(response.getStatus());
        System.out.println(response.body());
    }
}
