package fun.javierchen.jcaiagentbackend.tools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    private static WebSearchTool webSearchTool;

    @BeforeAll
    public static void init() {
        webSearchTool = new WebSearchTool();
    }

    @Test
    public void serach() throws Exception {
        String 五月天 = webSearchTool.searchBaiDu("五月天", 5);
        assertNotNull(五月天);
        System.out.println(五月天);
    }

}