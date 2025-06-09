package fun.javierchen.jcaiagentbackend.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


public class WebSearchTool {

    private final String searchAPIKey;

    public WebSearchTool() {
        searchAPIKey = System.getenv("SEARCH_API_KEY");
    }

    @Tool(description = "search 5 result from web")
    public String search(@ToolParam(description = "search query") String query) {
        return search(query, 5); // 默认返回前5条
    }

    @Tool(description = "search something from web use the number of top result")
    public String search(@ToolParam(description = "search query") String query,@ToolParam(description = "return the number of result") int topN) {
        String url = "https://www.searchapi.io/api/v1/search";
        String result = HttpRequest.get(url)
                .form("engine", "baidu")
                .form("q", query)
                .form("api_key", searchAPIKey)
//                .timeout(5000)
                .execute()
                .body();

        return parseResults(result, topN);
    }

    private String parseResults(String jsonStr, int topN) {
        JSONObject json = JSONUtil.parseObj(jsonStr);
        JSONArray results = json.getJSONArray("organic_results");


        // 计算实际返回条数
        int returnCount = Math.min(results.size(), Math.max(topN, 1)); // 至少返回1条

        StringBuilder sb = new StringBuilder("搜索结果（显示前")
                .append(returnCount).append("条）：\n");

        for (int i = 0; i < returnCount; i++) {
            JSONObject item = results.getJSONObject(i);
            sb.append(i + 1).append(". ")
                    .append(item.getStr("title")).append("\n")
                    .append("   链接：").append(item.getStr("link")).append("\n")
                    .append("   摘要：").append(item.getStr("snippet")).append("\n\n");
        }
        return sb.toString();
    }

}
