package fun.javierchen.jcaiagentbackend.agent.quiz.tools;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 答案评估工具
 * 使用 LLM 评估用户答案
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerEvaluatorTool implements AgentTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ChatClient.Builder chatClientBuilder;

    private static final String TOOL_NAME = "AnswerEvaluator";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "评估用户的答案并分析认知状态";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String questionText = (String) params.get("questionText");
            String questionType = (String) params.get("questionType");
            String correctAnswer = (String) params.get("correctAnswer");
            String userAnswer = (String) params.get("userAnswer");
            Integer responseTimeMs = (Integer) params.getOrDefault("responseTimeMs", 0);
            Integer avgResponseTimeMs = (Integer) params.getOrDefault("avgResponseTimeMs", 30000);

            log.info("评估答案: type={}", questionType);

            // 对于客观题，直接判断
            QuestionType type = QuestionType.fromCode(questionType);
            if (type != null && type.isObjective()) {
                return evaluateObjective(type, correctAnswer, userAnswer, responseTimeMs, avgResponseTimeMs);
            }

            // 对于主观题，使用 LLM 评估
            return evaluateSubjective(questionText, correctAnswer, userAnswer,
                    responseTimeMs, avgResponseTimeMs);

        } catch (Exception e) {
            log.error("答案评估失败", e);
            return ToolResult.failure("答案评估失败: " + e.getMessage(), TOOL_NAME);
        }
    }

    /**
     * 评估客观题
     */
    private ToolResult evaluateObjective(QuestionType type, String correctAnswer,
            String userAnswer, int responseTimeMs, int avgResponseTimeMs) {
        Map<String, Object> result = new HashMap<>();

        boolean isCorrect;
        int score;

        switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                isCorrect = correctAnswer.equalsIgnoreCase(userAnswer.trim());
                score = isCorrect ? 100 : 0;
            }
            case MULTIPLE_SELECT -> {
                String[] correct = correctAnswer.split(",");
                String[] user = userAnswer.split(",");
                int matchCount = 0;
                for (String c : correct) {
                    for (String u : user) {
                        if (c.trim().equalsIgnoreCase(u.trim())) {
                            matchCount++;
                            break;
                        }
                    }
                }
                isCorrect = matchCount == correct.length && user.length == correct.length;
                score = correct.length > 0 ? matchCount * 100 / correct.length : 0;
            }
            case FILL_IN_BLANK -> {
                // 支持多个正确答案 (用 | 分隔)
                String[] alternatives = correctAnswer.split("\\|");
                isCorrect = false;
                for (String alt : alternatives) {
                    if (alt.trim().equalsIgnoreCase(userAnswer.trim())) {
                        isCorrect = true;
                        break;
                    }
                }
                score = isCorrect ? 100 : 0;
            }
            case ORDERING -> {
                isCorrect = correctAnswer.equals(userAnswer.trim());
                score = isCorrect ? 100 : 0;
            }
            default -> {
                isCorrect = false;
                score = 0;
            }
        }

        // 计算认知负荷信号
        boolean isSlowResponse = responseTimeMs > avgResponseTimeMs * 1.5;
        int loadScore = calculateLoadScore(isSlowResponse, false, !isCorrect);
        int depthScore = isCorrect ? 70 : 30;

        result.put("isCorrect", isCorrect);
        result.put("score", score);
        result.put("depthScore", depthScore);
        result.put("loadScore", loadScore);
        result.put("hesitationDetected", false);
        result.put("confusionDetected", false);
        result.put("feedback", isCorrect ? "回答正确！" : "回答错误，正确答案是: " + correctAnswer);
        result.put("conceptMastery", isCorrect ? "PARTIAL" : "UNMASTERED");

        return ToolResult.successAndTerminal(result, TOOL_NAME);
    }

    /**
     * 评估主观题 (使用 LLM)
     */
    private ToolResult evaluateSubjective(String questionText, String correctAnswer,
            String userAnswer, int responseTimeMs, int avgResponseTimeMs) {
        try {
            String prompt = buildEvaluationPrompt(questionText, correctAnswer, userAnswer,
                    responseTimeMs, avgResponseTimeMs);

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析评估结果
            Map<String, Object> result = parseEvaluationResult(response, responseTimeMs, avgResponseTimeMs,
                    userAnswer, correctAnswer);

            return ToolResult.successAndTerminal(result, TOOL_NAME);
        } catch (Exception e) {
            log.error("主观题评估失败", e);
            // 降级处理: 给予基础分数
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("isCorrect", false);
            fallback.put("score", 50);
            fallback.put("feedback", "评估服务暂时不可用，已给予基础分数");
            fallback.put("conceptMastery", "PARTIAL");
            return ToolResult.successAndTerminal(fallback, TOOL_NAME);
        }
    }

    /**
     * 构建评估提示词
     */
    private String buildEvaluationPrompt(String questionText, String correctAnswer,
            String userAnswer, int responseTimeMs, int avgResponseTimeMs) {
        return String.format("""
                你是一个智能评估助手，负责评估用户的答案并分析其认知状态。

                ## 题目信息
                - 题目: %s
                - 正确答案: %s

                ## 用户回答
                - 答案: %s
                - 响应时间: %d 毫秒
                - 平均响应时间: %d 毫秒

                ## 评估任务
                请评估用户的回答，输出 JSON 格式:
                ```json
                {
                  "is_correct": true/false,
                  "score": 0-100,
                  "depth_score": 0-100,
                  "load_score": 0-100,
                  "hesitation_detected": true/false,
                  "confusion_detected": true/false,
                  "feedback": "给用户的反馈",
                  "concept_mastery": "MASTERED/PARTIAL/UNMASTERED"
                }
                ```
                只返回 JSON，不要附加额外说明。评分必须基于用户真实答案，不能忽略用户输入。
                """,
                questionText, correctAnswer, userAnswer, responseTimeMs, avgResponseTimeMs);
    }

    /**
     * 解析评估结果
     */
    private Map<String, Object> parseEvaluationResult(String response, int responseTimeMs, int avgResponseTimeMs,
            String userAnswer, String correctAnswer) {
        Map<String, Object> result = new HashMap<>();
        boolean isSlowResponse = responseTimeMs > avgResponseTimeMs * 1.5;
        try {
            String json = extractJson(response);
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            boolean isCorrect = readBoolean(parsed, "isCorrect", "is_correct");
            boolean hesitationDetected = readBoolean(parsed, "hesitationDetected", "hesitation_detected");
            boolean confusionDetected = readBoolean(parsed, "confusionDetected", "confusion_detected");
            int score = readInt(parsed, 60, "score");
            int depthScore = readInt(parsed, isCorrect ? 70 : 40, "depthScore", "depth_score");
            int loadScore = readInt(parsed,
                    calculateLoadScore(isSlowResponse, hesitationDetected, !isCorrect),
                    "loadScore", "load_score");
            String feedback = readString(parsed, "feedback");
            String conceptMastery = readString(parsed, "conceptMastery", "concept_mastery");

            result.put("isCorrect", isCorrect);
            result.put("score", clamp(score));
            result.put("depthScore", clamp(depthScore));
            result.put("loadScore", clamp(loadScore));
            result.put("hesitationDetected", hesitationDetected);
            result.put("confusionDetected", confusionDetected);
            result.put("feedback", feedback != null ? feedback : "回答分析完成");
            result.put("conceptMastery", normalizeConceptMastery(conceptMastery, isCorrect));
            return result;
        } catch (Exception e) {
            log.warn("解析评估结果失败，启用启发式兜底: {}", e.getMessage());
            boolean answerProvided = userAnswer != null && !userAnswer.isBlank();
            boolean overlapsReference = answerProvided && correctAnswer != null
                    && correctAnswer.toLowerCase().contains(userAnswer.trim().toLowerCase());

            result.put("isCorrect", overlapsReference);
            result.put("score", overlapsReference ? 80 : (answerProvided ? 50 : 0));
            result.put("depthScore", overlapsReference ? 70 : (answerProvided ? 50 : 20));
            result.put("loadScore", calculateLoadScore(isSlowResponse, false, !overlapsReference));
            result.put("hesitationDetected", false);
            result.put("confusionDetected", false);
            result.put("feedback", overlapsReference ? "回答与参考答案高度相关" : "已结合你的作答内容给出基础评估");
            result.put("conceptMastery", overlapsReference ? "PARTIAL" : "UNMASTERED");
            return result;
        }
    }

    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1).trim();
        }
        return response.trim();
    }

    private boolean readBoolean(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                return Boolean.parseBoolean(text);
            }
        }
        return false;
    }

    private int readInt(Map<String, Object> data, int defaultValue, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // keep trying
                }
            }
        }
        return defaultValue;
    }

    private String readString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String normalizeConceptMastery(String conceptMastery, boolean isCorrect) {
        if (conceptMastery == null || conceptMastery.isBlank()) {
            return isCorrect ? "PARTIAL" : "UNMASTERED";
        }
        String normalized = conceptMastery.trim().toUpperCase();
        return switch (normalized) {
            case "MASTERED", "PARTIAL", "UNMASTERED" -> normalized;
            default -> isCorrect ? "PARTIAL" : "UNMASTERED";
        };
    }

    /**
     * 计算认知负荷分数
     */
    private int calculateLoadScore(boolean isSlowResponse, boolean hesitation, boolean isWrong) {
        int score = 20; // 基础分
        if (isSlowResponse)
            score += 30;
        if (hesitation)
            score += 30;
        if (isWrong)
            score += 20;
        return Math.min(100, score);
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        return Map.of(
                "questionText", "题目文本",
                "questionType", "题目类型",
                "correctAnswer", "正确答案",
                "userAnswer", "用户答案",
                "responseTimeMs", "用户响应时间(毫秒)");
    }
}
