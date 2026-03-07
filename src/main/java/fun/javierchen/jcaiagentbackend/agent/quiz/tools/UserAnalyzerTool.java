package fun.javierchen.jcaiagentbackend.agent.quiz.tools;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.ToolResult;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.repository.QuestionResponseRepository;
import fun.javierchen.jcaiagentbackend.repository.UserKnowledgeStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户分析工具
 * 分析用户的认知状态
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAnalyzerTool implements AgentTool {

    private final UserKnowledgeStateRepository knowledgeStateRepository;
    private final QuestionResponseRepository responseRepository;

    private static final String TOOL_NAME = "UserAnalyzer";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "分析用户的认知状态和知识掌握情况";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            Long userId = (Long) params.get("userId");
            Long tenantId = (Long) params.get("tenantId");

            if (userId == null) {
                return ToolResult.failure("用户ID不能为空", TOOL_NAME);
            }

            log.info("分析用户认知状态: userId={}", userId);

            // 获取用户知识状态
            List<UserKnowledgeState> states = knowledgeStateRepository.findActiveByUserId(userId);

            // 计算统计数据
            Long masteredCount = knowledgeStateRepository.countMasteredByUserId(userId);
            Long unmasteredCount = knowledgeStateRepository.countUnmasteredByUserId(userId);
            Double avgDepth = knowledgeStateRepository.findAvgDepthByUserId(userId);
            Double avgLoad = knowledgeStateRepository.findAvgLoadByUserId(userId);
            Double avgStability = knowledgeStateRepository.findAvgStabilityByUserId(userId);

            // 获取回答统计
            long correctCount = responseRepository.countCorrectByUserId(userId);
            long totalCount = responseRepository.countActiveByUserId(userId);
            double accuracy = totalCount > 0 ? (double) correctCount / totalCount * 100 : 0;

            // 找出薄弱知识点
            List<UserKnowledgeState> struggling = knowledgeStateRepository.findStrugglingByUserId(userId);

            // 构建分析结果
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("totalTopics", states.size());
            analysis.put("masteredCount", masteredCount != null ? masteredCount : 0);
            analysis.put("unmasteredCount", unmasteredCount != null ? unmasteredCount : 0);
            analysis.put("avgDepth", avgDepth != null ? avgDepth.intValue() : 50);
            analysis.put("avgLoad", avgLoad != null ? avgLoad.intValue() : 50);
            analysis.put("avgStability", avgStability != null ? avgStability.intValue() : 50);
            analysis.put("totalAnswers", totalCount);
            analysis.put("correctAnswers", correctCount);
            analysis.put("accuracy", Math.round(accuracy * 10) / 10.0);
            analysis.put("strugglingTopics", struggling.stream()
                    .map(UserKnowledgeState::getTopicName)
                    .limit(5)
                    .toList());

            // 生成建议
            String recommendation = generateRecommendation(
                    avgDepth != null ? avgDepth.intValue() : 50,
                    avgLoad != null ? avgLoad.intValue() : 50,
                    avgStability != null ? avgStability.intValue() : 50);
            analysis.put("recommendation", recommendation);

            log.info("用户分析完成: 掌握率={}%",
                    states.isEmpty() ? 0 : masteredCount * 100 / states.size());
            return ToolResult.success(analysis, TOOL_NAME);

        } catch (Exception e) {
            log.error("用户分析失败", e);
            return ToolResult.failure("用户分析失败: " + e.getMessage(), TOOL_NAME);
        }
    }

    /**
     * 生成改进建议
     */
    private String generateRecommendation(int depth, int load, int stability) {
        if (load > 60 && stability < 50) {
            return "建议降低难度，从基础知识开始巩固";
        } else if (depth < 50 && load < 40) {
            return "可以增加解释类题目，深化概念理解";
        } else if (depth >= 70 && load <= 40 && stability >= 70) {
            return "掌握良好，可以尝试更高难度的内容";
        } else if (stability < 50) {
            return "建议对薄弱知识点进行针对性练习";
        } else {
            return "继续保持当前的学习节奏";
        }
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        return Map.of(
                "userId", "用户ID",
                "tenantId", "租户ID");
    }
}
