package fun.javierchen.jcaiagentbackend.app;

import cn.hutool.core.util.StrUtil;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    void doChatWithStream() throws InterruptedException {
        String chatId = "100";
        String question = "现在，我已经结婚，对于关系的维护十分犯难，你有什么解决方案吗？";
        StringBuffer content = new StringBuffer();
        CountDownLatch latch = new CountDownLatch(1);
        loveApp.doChatWithStream(question, chatId, chunk -> {
                    System.out.print(chunk);
                    content.append(chunk);
                },
                latch::countDown);

        // ⭐⭐⭐ 阻塞主线程直到流处理完成
        boolean completed = latch.await(30, TimeUnit.SECONDS); // 设置最大等待时间避免死锁

        if (completed) {
            fail("流未如期完成");
        }

        System.out.println("\n完整回复内容为：");
        System.out.println(content);
    }

    @Test
    void doChatWithMultiLanguage() {
        String chatId = "100";
        String question = "Hello, I am currently struggling to maintain a relationship after marriage. Please give me some advice";
        String s = loveApp.doChatWithMultiLanguage(question, chatId, "zh");
        System.out.println(s);
        assertTrue(StrUtil.isNotBlank(s));
    }

    @Test
    void doChatWithTool() {
        String chatId = "100";
        String question = "我想找一个对象 帮我把相应的建议发送到邮箱: 3270260751@qq.com ";
        String s = loveApp.doChatWithTool(question, chatId);
        System.out.println(s);
    }

    @Test
    void doChat() {
    }

    @Test
    void doChatWithMCP() {
        String chatId = "100";
        String question = "现在，我已经结婚，想和爱人去太原约会地点，我们该去哪里？";
        String s = loveApp.doChatWithMCP(question, chatId);
        System.out.println(s);
    }

}