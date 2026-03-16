package fun.javierchen.jcaiagentbackend.agent.quiz.inventory;

import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import fun.javierchen.jcaiagentbackend.rag.config.VectorStoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 概念清单服务
 * 负责从知识库文档中提取核心概念，并缓存到 Redis
 *
 * @author JavierChen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptInventoryService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStoreService vectorStoreService;
    private final QuizRedisService quizRedisService;

    private static final int MAX_CHUNKS_FOR_EXTRACTION = 30;

    /**
     * 提取概念清单并写入 Redis + agentState
     *
     * @param sessionId   会话 ID
     * @param tenantId    租户 ID
     * @param documentIds 文档范围
     * @return 概念名称集合
     */
    public Set<String> extractAndCacheConcepts(String sessionId, Long tenantId,
            List<Long> documentIds) {
        log.info("开始提取概念清单: sessionId={}, documentIds={}", sessionId, documentIds);

        // 1. 从向量库中检索文档片段
        String documentContent = retrieveDocumentContent(documentIds, tenantId);

        if (documentContent.isBlank()) {
            log.warn("无法获取文档内容用于概念提取");
            return Set.of();
        }

        // 2. 使用 LLM 提取概念
        Set<String> concepts = extractConceptsByLLM(documentContent);

        if (concepts.isEmpty()) {
            log.warn("LLM 未提取到任何概念");
            return Set.of();
        }

        // 3. 写入 Redis
        try {
            quizRedisService.saveConcepts(sessionId, concepts);
        } catch (Exception e) {
            log.warn("概念清单写入 Redis 失败，将仅依赖 agentState: {}", e.getMessage());
        }

        log.info("概念提取完成: sessionId={}, 提取到 {} 个概念", sessionId, concepts.size());
        return concepts;
    }

    /**
     * 获取概念清单（优先 Redis，降级 agentState）
     */
    public Set<String> getConcepts(String sessionId, Map<String, Object> agentState) {
        // 优先 Redis
        try {
            Set<String> redisConcepts = quizRedisService.getConcepts(sessionId);
            if (!redisConcepts.isEmpty()) {
                return redisConcepts;
            }
        } catch (Exception e) {
            log.debug("Redis 读取概念清单失败: {}", e.getMessage());
        }

        // 降级 agentState
        if (agentState != null) {
            Object concepts = agentState.get("concepts");
            if (concepts instanceof Collection<?> col) {
                return col.stream().map(Object::toString).collect(Collectors.toSet());
            }
        }

        return Set.of();
    }

    /**
     * 从向量库检索文档内容用于概念提取
     */
    private String retrieveDocumentContent(List<Long> documentIds, Long tenantId) {
        StringBuilder sb = new StringBuilder();

        if (documentIds == null || documentIds.isEmpty()) {
            // 无文档范围，使用通用检索
            List<Document> docs = vectorStoreService.similaritySearch("概述 总结 核心概念", MAX_CHUNKS_FOR_EXTRACTION);
            for (Document doc : docs) {
                sb.append(doc.getText()).append("\n\n");
            }
            return sb.toString();
        }

        // 按文档 ID 检索
        for (Long docId : documentIds) {
            List<Document> docs = vectorStoreService.similaritySearchByDocument(
                    "概述 总结 核心概念 关键知识点", Math.min(10, MAX_CHUNKS_FOR_EXTRACTION / documentIds.size()),
                    docId, tenantId);
            for (Document doc : docs) {
                sb.append(doc.getText()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * 使用 LLM 从文档内容中提取核心概念
     */
    private Set<String> extractConceptsByLLM(String documentContent) {
        // 截断过长的内容
        String truncated = documentContent.length() > 8000
                ? documentContent.substring(0, 8000) + "\n...(内容已截断)"
                : documentContent;

        String prompt = String.format("""
                请从以下学习材料中提取所有核心知识点/概念。

                要求：
                1. 每个概念用简洁的中文名称表示（2-10个字）
                2. 概念应该是可以独立出题考察的知识单元
                3. 不要重复，不要过于笼统（如"编程"）
                4. 返回 JSON 数组格式

                ## 学习材料
                %s

                ## 输出格式
                请直接返回 JSON 数组，不要包含其他文字：
                ```json
                ["概念1", "概念2", "概念3", ...]
                ```
                """, truncated);

        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder().build())
                    .build();

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseConceptList(response);
        } catch (Exception e) {
            log.error("LLM 概念提取失败: {}", e.getMessage(), e);
            return Set.of();
        }
    }

    /**
     * 解析 LLM 返回的概念列表
     */
    private Set<String> parseConceptList(String response) {
        try {
            String jsonStr = response;

            // 提取 JSON 数组
            if (response.contains("```json")) {
                int start = response.indexOf("```json") + 7;
                int end = response.indexOf("```", start);
                if (end > start) {
                    jsonStr = response.substring(start, end).trim();
                }
            } else if (response.contains("```")) {
                int start = response.indexOf("```") + 3;
                int end = response.indexOf("```", start);
                if (end > start) {
                    jsonStr = response.substring(start, end).trim();
                }
            } else {
                int start = response.indexOf("[");
                int end = response.lastIndexOf("]") + 1;
                if (start >= 0 && end > start) {
                    jsonStr = response.substring(start, end);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            List<String> concepts = mapper.readValue(jsonStr, new TypeReference<List<String>>() {});

            // 过滤空值和过长的名称
            return concepts.stream()
                    .filter(c -> c != null && !c.isBlank() && c.length() <= 50)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.error("解析概念列表失败: {}", e.getMessage());
            return Set.of();
        }
    }
}
