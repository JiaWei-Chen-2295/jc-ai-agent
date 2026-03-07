package fun.javierchen.jcaiagentbackend.agent.quiz.tools;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识检索工具
 * 从向量库检索相关知识
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrieverTool implements AgentTool {

    private final VectorStore studyFriendPGvectorStore;

    private static final String TOOL_NAME = "KnowledgeRetriever";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "从向量库检索相关知识内容";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String query = (String) params.getOrDefault("query", "");
            Integer topK = (Integer) params.getOrDefault("topK", 5);
            Double similarityThreshold = (Double) params.getOrDefault("threshold", 0.7);
            @SuppressWarnings("unchecked")
            List<Long> documentIds = (List<Long>) params.get("documentIds");

            log.info("知识检索: query={}, topK={}", query, topK);

            if (query.isBlank()) {
                return ToolResult.failure("查询内容不能为空", TOOL_NAME);
            }

            // 构建检索请求
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();

            // 执行检索
            List<Document> documents = studyFriendPGvectorStore.similaritySearch(request);

            if (documents.isEmpty()) {
                log.info("未找到相关文档");
                return ToolResult.success(List.of(), TOOL_NAME);
            }

            // 转换结果
            List<Map<String, Object>> results = new ArrayList<>();
            for (Document doc : documents) {
                Map<String, Object> result = new HashMap<>();
                result.put("content", doc.getText());
                result.put("metadata", doc.getMetadata());
                results.add(result);
            }

            log.info("检索到 {} 个相关文档", results.size());
            return ToolResult.success(results, TOOL_NAME);

        } catch (Exception e) {
            log.error("知识检索失败", e);
            return ToolResult.failure("知识检索失败: " + e.getMessage(), TOOL_NAME);
        }
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        return Map.of(
                "query", "检索查询内容",
                "topK", "返回的最大文档数",
                "threshold", "相似度阈值",
                "documentIds", "限定的文档ID列表");
    }
}
