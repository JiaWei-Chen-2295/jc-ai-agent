package fun.javierchen.jcaiagentbackend.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class WebScrapingToolTest {

    @Test
    public void testGetHtml() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String html = webScrapingTool.getHtml("https://www.javierchen.fun");
        assertNotNull(html);
        System.out.println(html + "\n");
    }


}