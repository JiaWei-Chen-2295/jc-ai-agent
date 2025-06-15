package fun.javierchen.jcimagesearchserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    public void testSearchImage() {
        String result = imageSearchTool.searchImage("cat");
        assertNotNull(result);
    }
}