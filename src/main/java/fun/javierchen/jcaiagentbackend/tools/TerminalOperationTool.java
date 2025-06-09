package fun.javierchen.jcaiagentbackend.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.Charset;

public class TerminalOperationTool {
    @Tool(description = "Execute a terminal command")
    public String executeCommand(@ToolParam(description = "the command to execute") String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            // 使用系统默认编码（推荐）
            String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
            String errorOutput = new String(process.getErrorStream().readAllBytes(), Charset.defaultCharset());

            if (exitCode == 0) {
                return "Command executed successfully:\n" + output;
            } else {
                return "Command failed with exit code " + exitCode + ":\nStandard Output:\n" + output + "\nError Output:\n" + errorOutput;
            }
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

}
