package fun.javierchen.jcaiagentbackend.app;

import cn.hutool.core.util.StrUtil;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    public void testDoChat() {
        String question = "你好 我是JC";
        String answer = loveApp.doChat(question, "100");
        Assertions.assertNotNull(answer);

        question = "我现在失恋了 患得患失 如何走出这个心境";
        answer = loveApp.doChat(question, "100");
        Assertions.assertNotNull(answer);

        question = "还记得我是谁吗？";
        answer = loveApp.doChat(question, "100");
        Assertions.assertNotNull(answer);

    }

}