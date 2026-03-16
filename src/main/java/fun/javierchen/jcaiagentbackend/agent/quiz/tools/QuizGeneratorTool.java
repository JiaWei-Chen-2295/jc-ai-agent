package fun.javierchen.jcaiagentbackend.agent.quiz.tools;

import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.ToolResult;
import fun.javierchen.jcaiagentbackend.rag.model.entity.StudyFriendDocument;
import fun.javierchen.jcaiagentbackend.repository.StudyFriendDocumentRepository;
import fun.javierchen.jcaiagentbackend.utils.VectorStoreFilterUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目生成工具
 * 使用 LLM 和向量库生成测验题目
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizGeneratorTool implements AgentTool {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore studyFriendPGvectorStore;
    private final StudyFriendDocumentRepository documentRepository;
    private final QuizRedisService quizRedisService;

    private static final String TOOL_NAME = "QuizGenerator";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "根据知识库内容生成测验题目";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String topic = (String) params.getOrDefault("topic", "");
            Integer count = (Integer) params.getOrDefault("count", 3);
            String difficulty = (String) params.getOrDefault("difficulty", "MEDIUM");
            Integer depth = (Integer) params.getOrDefault("understandingDepth", 50);
            Integer load = (Integer) params.getOrDefault("cognitiveLoad", 50);
            Integer stability = (Integer) params.getOrDefault("stability", 50);
            Boolean forcedFallback = (Boolean) params.getOrDefault("forcedFallback", false);
            String fallbackReason = (String) params.getOrDefault("fallbackReason", "");
            Object recentResponses = params.get("recentResponses");
            @SuppressWarnings("unchecked")
            List<Long> documentIds = (List<Long>) params.get("documentIds");
            String sessionId = (String) params.get("sessionId");
            Long tenantId = params.get("tenantId") instanceof Number n ? n.longValue() : null;

            log.info("生成题目: topic={}, count={}, difficulty={}, tenantId={}", topic, count, difficulty, tenantId);

            // 从向量库检索相关知识 (Phase 3: 去重 + 多样化)
            List<Document> docs = retrieveKnowledge(topic, documentIds, sessionId, tenantId);

            String knowledgeContent;
            boolean fallbackMode = Boolean.TRUE.equals(forcedFallback);

            if (docs.isEmpty()) {
                // 向量检索失败，尝试基于文档元信息生成题目
                log.info("向量检索无结果，尝试基于文档元信息生成题目");
                knowledgeContent = buildDocumentMetaContent(documentIds, topic);
                fallbackMode = true;
            } else {
                // 构建知识内容
                StringBuilder sb = new StringBuilder();
                for (Document doc : docs) {
                    sb.append(doc.getText()).append("\n\n");
                }
                knowledgeContent = sb.toString();
            }

            // Phase 4: 获取已知概念名列表用于 prompt 注入
            Set<String> knownConcepts = Set.of();
            if (sessionId != null) {
                try {
                    knownConcepts = quizRedisService.getConcepts(sessionId);
                } catch (Exception e) {
                    log.debug("获取概念清单失败: {}", e.getMessage());
                }
            }

            // 生成题目
            String prompt = fallbackMode
                    ? buildFallbackPrompt(knowledgeContent, count, difficulty, depth, load, stability,
                            fallbackReason, recentResponses)
                    : buildPrompt(knowledgeContent, count, difficulty, depth, load, stability,
                            recentResponses, fallbackReason, knownConcepts);

            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder().build())
                    .build();
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析响应
            List<Map<String, Object>> questions = parseQuestions(response);
            if (questions.isEmpty()) {
                log.warn("LLM 未返回可解析题目，启用本地兜底出题");
                questions = buildEmergencyFallbackQuestions(topic, difficulty, count, knowledgeContent);
                fallbackMode = true;
                if (fallbackReason == null || fallbackReason.isBlank()) {
                    fallbackReason = "模型返回结果不可解析，已切换为本地兜底题目";
                }
            }

            log.info("成功生成 {} 道题目", questions.size());
            return ToolResult.successAndTerminal(questions, TOOL_NAME);

        } catch (Exception e) {
            log.error("题目生成失败", e);
            return ToolResult.failure("题目生成失败: " + e.getMessage(), TOOL_NAME);
        }
    }

    /**
     * 从向量库检索相关知识
     * Phase 3: topK 提升到 15, 去重已用 chunk, 取前 5 个未用的
     */
    private List<Document> retrieveKnowledge(String topic, List<Long> documentIds, String sessionId, Long tenantId) {
        try {
            // 确保 TenantContextHolder 可用，防止向量检索因"租户未选择"失败
            Long prevTenantId = TenantContextHolder.getTenantId();
            boolean tenantIdRestored = false;
            if (prevTenantId == null && tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
                tenantIdRestored = true;
                log.debug("retrieveKnowledge: 补设 TenantContextHolder, tenantId={}", tenantId);
            }

            try {
                return doRetrieveKnowledge(topic, documentIds, sessionId);
            } finally {
                if (tenantIdRestored) {
                    TenantContextHolder.clear();
                }
            }
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 实际执行向量检索逻辑（TenantContextHolder 已就绪）
     */
    private List<Document> doRetrieveKnowledge(String topic, List<Long> documentIds, String sessionId) {
        try {
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(topic)
                    .topK(15);  // Phase 3: 从 5 提升到 15

            // 如果指定了文档范围，添加过滤条件
            if (documentIds != null && !documentIds.isEmpty()) {
                Filter.Expression filter = VectorStoreFilterUtils.buildDocumentIdFilter(documentIds);
                if (filter != null) {
                    requestBuilder.filterExpression(filter);
                }
            }

            List<Document> allDocs = studyFriendPGvectorStore.similaritySearch(requestBuilder.build());

            // Phase 3: 去重已用 chunk
            if (sessionId != null && !allDocs.isEmpty()) {
                Set<String> usedChunks = quizRedisService.getUsedChunks(sessionId);
                log.debug("chunk 去重: 检索到={}, 已用={}", allDocs.size(), usedChunks.size());

                List<Document> unusedDocs = allDocs.stream()
                        .filter(doc -> !usedChunks.contains(doc.getId()))
                        .limit(5)
                        .collect(Collectors.toList());

                // 记录新使用的 chunk
                List<String> newChunkIds = unusedDocs.stream()
                        .map(Document::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (!newChunkIds.isEmpty()) {
                    quizRedisService.addUsedChunks(sessionId, newChunkIds);
                }

                // 如果未用的不足 5 个，补充已用的（避免无内容可出题）
                if (unusedDocs.size() < 3 && allDocs.size() > unusedDocs.size()) {
                    log.info("未用 chunk 不足，补充已用 chunk: unused={}, total={}",
                            unusedDocs.size(), allDocs.size());
                    for (Document doc : allDocs) {
                        if (unusedDocs.size() >= 5) break;
                        if (!unusedDocs.contains(doc)) {
                            unusedDocs.add(doc);
                        }
                    }
                }

                return unusedDocs;
            }

            // 无 sessionId 时取前 5
            return allDocs.stream().limit(5).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建题目生成提示词
     * Phase 4: 注入已知概念名列表，引导 LLM 使用标准名称
     */
    private String buildPrompt(String knowledge, int count, String difficulty,
            int depth, int load, int stability, Object recentResponses,
            String fallbackReason, Set<String> knownConcepts) {
        String questionTypes = selectQuestionTypes(load);
        String recentResponsesSection = buildRecentResponsesSection(recentResponses);
        String fallbackSection = (fallbackReason != null && !fallbackReason.isBlank())
                ? "## 额外调度信号\n- " + fallbackReason + "\n\n"
                : "";
        // Phase 4: 构建概念名列表段落
        String conceptSection = "";
        if (knownConcepts != null && !knownConcepts.isEmpty()) {
            conceptSection = "## 已知概念清单（related_concept 必须从以下列表中选取）\n"
                    + String.join("、", knownConcepts) + "\n\n";
        }
        return String.format("""
                你是一个智能测验助手，负责根据用户的知识库文档生成多元化的测验题目。

                ## 当前用户状态
                - 理解深度 (D): %d/100
                - 认知负荷 (L): %d/100 (越低越好)
                - 稳定性 (S): %d/100

                ## 目标难度
                %s

                ## 建议题型
                %s

                %s\
                %s\
                %s\
                ## 知识库内容
                %s

                ## 题目类型说明
                请使用以下标准题目类型代码：
                - SINGLE_CHOICE: 单选题（有多个选项，只有一个正确答案）
                - MULTIPLE_CHOICE: 多选题（有多个选项，有多个正确答案）
                - TRUE_FALSE: 判断题（判断对错）
                - FILL_IN_THE_BLANK: 填空题（填写空白处内容，不需要 options）
                - SHORT_ANSWER: 简答题（简短回答，不需要 options）
                - EXPLANATION: 解释题（解释原理，不需要 options）

                ## 输出格式
                请生成 %d 道题目，使用 JSON 格式输出:
                ```json
                {
                  "questions": [
                    {
                      "type": "SINGLE_CHOICE",
                      "text": "题目内容",
                      "options": ["选项A", "选项B", "选项C", "选项D"],
                      "correct_answer": "正确答案",
                      "related_concept": "关联知识点",
                      "difficulty": "%s",
                      "explanation": "答案解释"
                    }
                  ]
                }
                ```

                注意:
                1. 题目应紧密围绕知识库内容
                2. type 必须使用上述标准代码（全大写下划线格式）
                3. 单选题 correct_answer 为单个选项（如 "A"）
                4. 多选题 correct_answer 为逗号分隔的选项（如 "A,B,D"）
                5. 填空题、简答题、解释题不需要 options 字段
                6. difficulty 必须是 EASY、MEDIUM 或 HARD 之一
                7. 每道题要有明确的知识点关联
                8. 要结合最近作答情况，避免重复问法，并针对用户的薄弱点出题
                9. related_concept 必须使用已知概念清单中的名称（如果提供了概念清单）
                """,
                depth, load, stability, difficulty, questionTypes,
                fallbackSection, recentResponsesSection, conceptSection,
                knowledge, count, difficulty);
    }

    /**
     * 根据认知负荷选择合适的题型
     */
    private String selectQuestionTypes(int cognitiveLoad) {
        if (cognitiveLoad > 60) {
            return "优先使用: 判断题(TRUE_FALSE)、单选题(SINGLE_CHOICE)";
        } else if (cognitiveLoad < 30) {
            return "可以使用: 解释题(EXPLANATION)、代码补全(CODE_COMPLETION)、排序题(ORDERING)";
        } else {
            return "混合使用: 单选、多选、填空、简答";
        }
    }

    /**
     * 构建文档元信息内容
     * 当向量检索失败时，基于文档标题生成概述性题目
     */
    private String buildDocumentMetaContent(List<Long> documentIds, String topic) {
        StringBuilder sb = new StringBuilder();
        if (topic != null && !topic.isBlank()) {
            sb.append("当前测验主题：").append(topic).append("\n");
        }

        if (documentIds != null && !documentIds.isEmpty()) {
            List<StudyFriendDocument> documents = documentRepository.findByIds(documentIds);
            if (!documents.isEmpty()) {
                sb.append("用户的学习资料包括：\n");
                for (StudyFriendDocument doc : documents) {
                    sb.append("- ").append(doc.getFileName());
                    if (doc.getFileType() != null) {
                        sb.append(" (").append(doc.getFileType().toUpperCase()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("当前缺少可检索的文档片段，请围绕主题生成入门级基础题：").append(topic);
        }
        return sb.toString();
    }

    /**
     * 构建降级模式的提示词
     * 基于文档标题生成入门级概述性题目
     */
    private String buildFallbackPrompt(String documentMeta, int count, String difficulty,
            int depth, int load, int stability, String fallbackReason, Object recentResponses) {
        String questionTypes = selectQuestionTypes(load);
        String recentResponsesSection = buildRecentResponsesSection(recentResponses);
        String normalizedReason = (fallbackReason == null || fallbackReason.isBlank())
                ? "当前详细知识片段不可用，请生成更基础的过渡题目"
                : fallbackReason;

        return String.format("""
                你是一个智能测验助手。用户的知识库正在索引中，暂时无法检索详细内容。
                请根据用户学习资料的标题，生成一些概述性的入门题目，帮助用户了解这些主题的基础知识。

                ## 当前用户状态
                - 理解深度 (D): %d/100
                - 认知负荷 (L): %d/100 (越低越好)
                - 稳定性 (S): %d/100

                ## 目标难度
                %s

                ## 建议题型
                %s

                ## 降级原因
                %s

                %s\
                ## 用户学习资料
                %s

                ## 题目类型说明
                请使用以下标准题目类型代码：
                - SINGLE_CHOICE: 单选题
                - TRUE_FALSE: 判断题
                - SHORT_ANSWER: 简答题

                ## 输出格式
                请生成 %d 道概述性入门题目，使用 JSON 格式输出:
                ```json
                {
                  "questions": [
                    {
                      "type": "SINGLE_CHOICE",
                      "text": "题目内容",
                      "options": ["选项A", "选项B", "选项C", "选项D"],
                      "correct_answer": "正确答案",
                      "related_concept": "关联知识点",
                      "difficulty": "%s",
                      "explanation": "答案解释"
                    }
                  ]
                }
                ```

                注意:
                1. 题目应为该主题的基础概念性题目
                2. 难度适当降低，以入门级为主
                3. related_concept 使用文档名称作为关联知识点
                4. type 必须使用标准代码（全大写下划线格式）
                5. 要避开用户刚刚已经答过的问法，并根据最近表现减轻负担
                """,
                depth, load, stability, difficulty, questionTypes, normalizedReason, recentResponsesSection,
                documentMeta, count, difficulty);
    }

    /**
     * 解析 LLM 生成的题目
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestions(String response) {
        List<Map<String, Object>> questions = new ArrayList<>();

        try {
            // 提取 JSON 部分 (处理可能的 markdown 代码块)
            String jsonStr = response;

            // 处理 ```json ... ``` 格式
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
                // 直接提取 JSON 对象
                int jsonStart = response.indexOf("{");
                int jsonEnd = response.lastIndexOf("}") + 1;
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    jsonStr = response.substring(jsonStart, jsonEnd);
                }
            }

            log.debug("解析JSON: {}", jsonStr.substring(0, Math.min(200, jsonStr.length())));

            // 使用 Jackson 解析 JSON
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> root = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {
            });

            // 提取 questions 数组
            if (root.containsKey("questions")) {
                Object questionsObj = root.get("questions");
                if (questionsObj instanceof List) {
                    List<Object> questionsList = (List<Object>) questionsObj;
                    for (Object q : questionsList) {
                        if (q instanceof Map) {
                            Map<String, Object> questionMap = (Map<String, Object>) q;
                            // 标准化字段名 (correct_answer -> correct_answer)
                            Map<String, Object> normalizedMap = new HashMap<>();
                            normalizedMap.put("type", questionMap.get("type"));
                            normalizedMap.put("text", questionMap.get("text"));
                            normalizedMap.put("options", questionMap.get("options"));
                            normalizedMap.put("correct_answer", questionMap.get("correct_answer"));
                            normalizedMap.put("related_concept", questionMap.get("related_concept"));
                            normalizedMap.put("difficulty", questionMap.get("difficulty"));
                            normalizedMap.put("explanation", questionMap.get("explanation"));
                            questions.add(normalizedMap);
                        }
                    }
                }
            }

            log.info("成功解析 {} 道题目", questions.size());

        } catch (Exception e) {
            log.error("解析题目响应失败: {}", e.getMessage(), e);
            // 解析失败时返回空列表，让上层处理
        }

        return questions;
    }

    private String buildRecentResponsesSection(Object recentResponses) {
        if (!(recentResponses instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## 最近作答情况\n");
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<?, ?> map = rawMap;
                sb.append("- 题目: ").append(stringValue(map.get("questionText"))).append("\n");
                sb.append("  用户回答: ").append(stringValue(map.get("userAnswer"))).append("\n");
                sb.append("  是否正确: ").append(stringValue(map.get("isCorrect"))).append("\n");
                sb.append("  反馈: ").append(stringValue(map.get("feedback"))).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Map<String, Object>> buildEmergencyFallbackQuestions(String topic, String difficulty,
            int count, String knowledgeContent) {
        List<Map<String, Object>> questions = new ArrayList<>();
        String normalizedTopic = (topic == null || topic.isBlank()) ? "当前学习主题" : topic;
        int total = Math.max(1, count);

        for (int i = 0; i < total; i++) {
            Map<String, Object> question = new HashMap<>();
            if (i % 3 == 0) {
                question.put("type", "SHORT_ANSWER");
                question.put("text", "请用一句话概括“" + normalizedTopic + "”最核心的概念。");
                question.put("options", null);
                question.put("correct_answer", "围绕核心定义作答即可");
                question.put("related_concept", normalizedTopic);
                question.put("difficulty", difficulty);
                question.put("explanation", "这道题用于确认你是否抓住主题的基本定义。");
            } else if (i % 3 == 1) {
                question.put("type", "TRUE_FALSE");
                question.put("text", "判断题：学习“" + normalizedTopic + "”时，只需要记结论，不需要理解原因。");
                question.put("options", List.of("TRUE", "FALSE"));
                question.put("correct_answer", "FALSE");
                question.put("related_concept", normalizedTopic);
                question.put("difficulty", "EASY");
                question.put("explanation", "理解原因和适用场景，通常比只记结论更重要。");
            } else {
                question.put("type", "SHORT_ANSWER");
                question.put("text", "结合资料“" + summarizeKnowledgeSource(knowledgeContent) + "”，说出一个你认为最容易混淆的点。");
                question.put("options", null);
                question.put("correct_answer", "言之有理即可");
                question.put("related_concept", normalizedTopic);
                question.put("difficulty", "EASY");
                question.put("explanation", "这道题用于暴露认知盲点，方便后续继续追问。");
            }
            questions.add(question);
        }

        return questions;
    }

    private String summarizeKnowledgeSource(String knowledgeContent) {
        if (knowledgeContent == null || knowledgeContent.isBlank()) {
            return "当前主题";
        }
        String compact = knowledgeContent.replace("\r", " ").replace("\n", " ").trim();
        return compact.substring(0, Math.min(24, compact.length()));
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        return Map.of(
                "topic", "题目主题",
                "count", "题目数量",
                "difficulty", "难度等级",
                "documentIds", "文档ID列表");
    }
}
