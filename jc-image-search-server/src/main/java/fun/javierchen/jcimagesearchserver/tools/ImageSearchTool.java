package fun.javierchen.jcimagesearchserver.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.Map;

@Service
public class ImageSearchTool {

    @Value("${jc-image-search-server.pexels.key}")
    private String API_KEY;
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "Search image from web using English keyword.")
    public String searchImage(@ToolParam(description = "Search query keyword in English") String keyword) {
        // 构建请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);
        params.put("per_page", 20);

        HttpResponse response = HttpRequest.get(API_URL)
                .header("Authorization", API_KEY)
                .form(params)
                .execute();

        if (response.isOk()) {
            String rawJson = response.body();
            return formatPexelsResponse(rawJson); // 格式化后返回
        } else {
            return "{\"error\": \"Failed to fetch images\", \"status\": " + response.getStatus() + "}";
        }
    }


    public String formatPexelsResponse(String rawJson) {
        JSONObject raw = JSONUtil.parseObj(rawJson);

        JSONObject result = new JSONObject();
        result.put("page", raw.getInt("page"));
        result.put("per_page", raw.getInt("per_page"));
        result.put("total_results", raw.getJSONArray("photos").size());

        JSONArray imagesArray = new JSONArray();
        for (Object photoObj : raw.getJSONArray("photos")) {
            JSONObject photo = (JSONObject) photoObj;

            JSONObject image = new JSONObject();
            image.set("id", photo.get("id"));
            image.put("url", photo.getJSONObject("src").get("original"));
            image.put("thumbnail", photo.getJSONObject("src").get("tiny"));
            image.set("description", photo.get("alt"));
            image.set("photographer", photo.get("photographer"));

            imagesArray.add(image);
        }

        result.put("images", imagesArray);
        return result.toString();
    }


}
