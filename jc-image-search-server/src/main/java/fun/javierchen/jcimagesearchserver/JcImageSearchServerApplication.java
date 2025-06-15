package fun.javierchen.jcimagesearchserver;

import fun.javierchen.jcimagesearchserver.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JcImageSearchServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JcImageSearchServerApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider imageSearchToolCallbackProvider(ImageSearchTool imageSearchTool) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(imageSearchTool)
				.build();
	}
}

