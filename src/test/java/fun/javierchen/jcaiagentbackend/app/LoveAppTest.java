package fun.javierchen.jcaiagentbackend.app;

import cn.hutool.core.util.StrUtil;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    public void testDoChat() {
        String chatId = UUID.randomUUID().toString();
        String question = "你好 我是JC";
        String answer = loveApp.doChat(question, chatId);
        Assertions.assertNotNull(answer);

        question = "我现在失恋了 患得患失 如何走出这个心境";
        answer = loveApp.doChat(question, chatId);
        Assertions.assertNotNull(answer);

        question = "还记得我是谁吗？";
        answer = loveApp.doChat(question, chatId);
        Assertions.assertNotNull(answer);

    }

    @Test
    void doChatWithReport() {
        String chatId = "100";
        String question = "你好，我是JC！我想让我在乎的人也在乎我，我该怎么办？";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(question, chatId);
        System.out.println(loveReport);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRAG() {

        String chatId = "100";
        String question = "现在，我已经结婚，对于关系的维护十分犯难，你有什么解决方案吗？";
        String answer = loveApp.doChatWithRAG(question, chatId);
        System.out.println(answer);
    }
}