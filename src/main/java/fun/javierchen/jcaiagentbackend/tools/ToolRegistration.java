package fun.javierchen.jcaiagentbackend.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 帮忙注册所有的类
 */
@Configuration
public class ToolRegistration {

    @Value("${jc-ai-agent.email_account}")
    private String emailAccount;

    @Value("${jc-ai-agent.email_password}")
    private String emailPassword;

    @Bean
    public ToolCallback[] toolCallback() {

        WebSearchTool webSearchTool = new WebSearchTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        SendMailTool sendMailTool = new SendMailTool(emailAccount, emailPassword);

        return ToolCallbacks.from(
                webSearchTool,
                webScrapingTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool,
                sendMailTool
        );
    }

}
