package fun.javierchen.jcaiagentbackend.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StudyFriendTest {

    @Resource
    private StudyFriend studyFriend;

    @Test
    public void doChatWithRAGTest() {
        String question = studyFriend.doChatWithRAG("请帮我提出5个关于计算机网络的问题", "10");
        assertNotNull(question);
        System.out.println(question);
        String answer = studyFriend.doChatWithRAG("给出刚刚的问题的答案和解释", "10");
        System.out.println(answer);
        assertNotNull(answer);
    }

    @Test
    public void doChatWithTools() {
        String question = studyFriend.doChatWithTools("请帮我提出5个关于计算机网络的问题，" +
                "把这个问题发送到邮件:1601020332@qq.com  3270260751@qq.com", "10");
        assertNotNull(question);
        System.out.println(question);

    }




}