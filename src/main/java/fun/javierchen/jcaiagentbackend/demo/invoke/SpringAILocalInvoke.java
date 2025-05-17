package fun.javierchen.jcaiagentbackend.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 使用 Spring AI 进行本地调用
 */
// 取消注释即可在 SpringBoot 项目启动时执行
//@Component
public class SpringAILocalInvoke implements CommandLineRunner {

    @Resource
    private ChatModel ollamaChatModel;  // 名称注册明确
    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = ollamaChatModel.call(new Prompt("你好，我是JC"))
                .getResult()
                .getOutput();
        System.out.println(output.getText());
    }

}
