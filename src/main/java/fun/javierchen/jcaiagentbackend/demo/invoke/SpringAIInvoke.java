package fun.javierchen.jcaiagentbackend.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 使用 Spring AI 进行智能调用
 */
// 注册为 Bean 才会调用
//@Component
public class SpringAIInvoke implements CommandLineRunner {
    @Resource
    private ChatModel dashscopeChatModel; // 确保名字是 dashscopeChatModel

    /**
     * spring 在
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = dashscopeChatModel.call(new Prompt("你是谁？"))
                .getResult()
                .getOutput();

        System.out.println(output.getText());
    }
}
