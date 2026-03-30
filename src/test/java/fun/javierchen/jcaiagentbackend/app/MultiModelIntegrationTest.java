package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.model.entity.AiModelConfig;
import fun.javierchen.jcaiagentbackend.repository.AiModelConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多模型集成测试
 * <p>
 * 从数据库读取已启用的模型，通过 ChatModelRegistry 解析后发送一条简单消息，
 * 验证端到端流程可用。
 * <p>
 * 前置条件：
 * <ol>
 *   <li>PostgreSQL 已运行，ai_model_config 表已建，至少有一条 enabled=true 的记录</li>
 *   <li>对应的 API Key 已加密写入 api_key_enc（DashScope 模型由环境变量管理）</li>
 *   <li>环境变量 JC_API_KEY_MASTER_SECRET 与加密时一致</li>
 * </ol>
 * <p>
 * 运行方式（Maven）：
 * <pre>mvn test -Dtest=MultiModelIntegrationTest -pl .</pre>
 * <p>
 * 运行方式（IntelliJ）：右键此类或单个方法 → Run
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
class MultiModelIntegrationTest {

    @Autowired
    private ChatModelRegistry chatModelRegistry;

    @Autowired
    private AiModelConfigRepository modelConfigRepository;

    /**
     * 遍历数据库中所有 enabled 的模型，逐一发送 "你好" 并验证能收到非空回复
     */
    @Test
    void testAllEnabledModels() {
        List<AiModelConfig> enabledModels =
                modelConfigRepository.findByEnabledTrueOrderBySortOrderAsc();
        assertFalse(enabledModels.isEmpty(), "数据库中没有已启用的模型，请先插入 ai_model_config 数据");

        for (AiModelConfig config : enabledModels) {
            String modelId = config.getModelId();
            log.info("========== 测试模型: {} ({}) ==========", config.getDisplayName(), modelId);

            ChatModel chatModel = chatModelRegistry.resolve(modelId);
            assertNotNull(chatModel, "ChatModelRegistry 返回 null: " + modelId);

            String response = ChatClient.builder(chatModel)
                    .build()
                    .prompt("你好，请用一句话介绍你自己。")
                    .call()
                    .content();

            log.info("模型 {} 回复: {}", modelId, response);
            assertNotNull(response, "模型 " + modelId + " 返回了 null");
            assertFalse(response.isBlank(), "模型 " + modelId + " 返回了空字符串");
        }
    }

    /**
     * 测试默认模型（qwen3-max）能正常解析和调用
     */
    @Test
    void testDefaultModel() {
        ChatModel chatModel = chatModelRegistry.resolve(null);
        assertNotNull(chatModel, "默认模型解析失败");

        String response = ChatClient.builder(chatModel)
                .build()
                .prompt("1+1=?")
                .call()
                .content();

        log.info("默认模型回复: {}", response);
        assertNotNull(response);
        assertFalse(response.isBlank());
    }
}
