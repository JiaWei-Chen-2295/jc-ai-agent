package fun.javierchen.jcaiagentbackend.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 帮忙注册所有的类
 */
@Configuration
public class ToolRegistration {

    @Bean
    public ToolCallback[] toolCallback() {

        WebSearchTool webSearchTool = new WebSearchTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        return ToolCallbacks.from(
                webSearchTool,
                webScrapingTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool
        );
    }

}
