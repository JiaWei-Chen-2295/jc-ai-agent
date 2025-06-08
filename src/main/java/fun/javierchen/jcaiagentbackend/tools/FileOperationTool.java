package fun.javierchen.jcaiagentbackend.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import fun.javierchen.jcaiagentbackend.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

public class FileOperationTool {

    String FILE_BASE_PATH = FileConstant.FILE_BASE_PATH + File.separator + "file";

    @Tool(description = "read content from file")
    public String readFile(@ToolParam(description = "file name to read") String FileName) {
        String content;
        try {
             content = FileUtil.readUtf8String(FILE_BASE_PATH + File.separator + FileName);
        } catch (IORuntimeException e) {
            return "Error reading file:" + e.getMessage();
        }
        return content;
    }

    /**
     *
     * @param FileName
     * @param content
     * @return 使用字符串可以更方便的把运行结果放到上下文中
     */
    @Tool(description = "write the content to file")
    public String writeFile(@ToolParam(description = "file name to write")String FileName,
                            @ToolParam(description = "content to write to file")String content) {
        String filePath = FILE_BASE_PATH + File.separator + FileName;
        FileUtil.mkdir(filePath);
        try {
            FileUtil.writeUtf8String(content, filePath);
            return String.format("File written successfully to %s.", filePath);
        } catch (IORuntimeException e) {
            return "Error writing file:" + e.getMessage();
        }
    }

}
