package fun.javierchen.jcaiagentbackend.tools;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WebScrapingTool {
    @Tool(description = "Scraping the content of a web page")
    public String getHtml(@ToolParam(description = "the url of web HTML to scrap") String url) {
        String html = null;
        try {
            html = Jsoup.connect(url).execute().body();
        } catch (Exception e) {
            return "Error scrap web page: " + e.getMessage();
        }
        return html;
    }
}
