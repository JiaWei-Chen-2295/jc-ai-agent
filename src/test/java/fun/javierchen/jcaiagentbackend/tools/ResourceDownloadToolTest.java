package fun.javierchen.jcaiagentbackend.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDownloadToolTest {

    @Test
    public void testDownloadFile() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String result = tool.downloadFile("https://fanyi-cdn.cdn.bcebos.com/static/cat/asset/logo.2481f256.png", "logo.png");
        System.out.println(result);
    }


}