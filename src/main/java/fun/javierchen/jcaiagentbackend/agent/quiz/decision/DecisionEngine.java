package fun.javierchen.jcaiagentbackend.agent.quiz.decision;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentContext;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentDecision;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentState;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策引擎
 * 根据用户状态和上下文做出智能决策
 *
 * @author JavierChen
 */
@Slf4j
@Component
public class DecisionEngine {

    /**
     * 状态指标的达标阈值
     */
    private static final int DEPTH_THRESHOLD = 70;
    private static final int LOAD_THRESHOLD = 40;
    private static final int STABILITY_THRESHOLD = 70;

    /**
     * 做出决策
     */
    public AgentDecision decide(AgentContext context, AgentState state) {
        log.debug("决策引擎分析上下文...");

        // 如果有用户输入，优先评估答案
        if (context.getUserInput() != null && !context.getUserInput().isBlank()) {
            return decideForAnswer(context, state);
        }

        // 分析用户知识状态
        List<UserKnowledgeState> knowledgeStates = context.getKnowledgeStates();

        // 计算综合指标
        CognitiveMetrics metrics = calculateMetrics(knowledgeStates);
        log.debug("认知指标: depth={}, load={}, stability={}",
                metrics.avgDepth, metrics.avgLoad, metrics.avgStability);

        // 判断是否已完成
        if (shouldFinish(metrics, knowledgeStates)) {
            return AgentDecision.finish("用户已掌握全部内容");
        }

        // 判断是否需要降级
        if (isStruggling(metrics)) {
            return buildQuestionGenerationDecision(context, metrics, knowledgeStates,
                    true, "用户认知负荷过高，需降级为更基础、更缓和的题目");
        }

        // 决定生成题目
        return decideForQuestionGeneration(context, metrics, knowledgeStates);
    }

    /**
     * 判断是否应该结束测验 (公开方法，供外部调用)
     * 结束条件: D ≥ 70, L ≤ 40, S ≥ 70 且所有知识点已掌握
     *
     * @param knowledgeStates 用户知识状态列表
     * @return 是否应该结束测验
     */
    public boolean checkShouldFinish(List<UserKnowledgeState> knowledgeStates) {
        CognitiveMetrics metrics = calculateMetrics(knowledgeStates);
        return shouldFinish(metrics, knowledgeStates);
    }

    /**
     * 获取当前认知指标 (公开方法，供外部调用)
     *
     * @param knowledgeStates 用户知识状态列表
     * @return 认知指标数组 [depth, load, stability]
     */
    public int[] getCognitiveMetrics(List<UserKnowledgeState> knowledgeStates) {
        CognitiveMetrics metrics = calculateMetrics(knowledgeStates);
        return new int[] { metrics.avgDepth, metrics.avgLoad, metrics.avgStability };
    }

    /**
     * 针对答案评估的决策
     */
    private AgentDecision decideForAnswer(AgentContext context, AgentState state) {
        Map<String, Object> params = new HashMap<>();
        params.put("userAnswer", context.getUserInput());
        params.put("responseTimeMs", context.getResponseTimeMs());
        
        // 从 extras 中获取题目信息
        if (context.getExtras() != null) {
            params.put("questionText", context.getExtras().get("questionText"));
            params.put("questionType", context.getExtras().get("questionType"));
            params.put("correctAnswer", context.getExtras().get("correctAnswer"));
            params.put("avgResponseTimeMs", context.getExtras().get("avgResponseTimeMs"));
        }

        return AgentDecision.callTool(
                "AnswerEvaluator",
                params,
                "用户提交了答案，需要评估");
    }

    /**
     * 针对题目生成的决策
     */
    private AgentDecision decideForQuestionGeneration(AgentContext context,
            CognitiveMetrics metrics, List<UserKnowledgeState> knowledgeStates) {
        return buildQuestionGenerationDecision(context, metrics, knowledgeStates, false, null);
    }

    private AgentDecision buildQuestionGenerationDecision(AgentContext context,
            CognitiveMetrics metrics, List<UserKnowledgeState> knowledgeStates,
            boolean forcedFallback, String fallbackReason) {

        Map<String, Object> params = new HashMap<>();

        // 确定题目数量
        int count = forcedFallback ? Math.min(2, determineQuestionCount(metrics)) : determineQuestionCount(metrics);
        params.put("count", count);

        // 确定难度
        String difficulty = forcedFallback ? "EASY" : determineDifficulty(metrics);
        params.put("difficulty", difficulty);

        // 确定主题
        String topic = determineTopic(knowledgeStates);
        params.put("topic", topic);

        // 传递认知状态
        params.put("understandingDepth", metrics.avgDepth);
        params.put("cognitiveLoad", metrics.avgLoad);
        params.put("stability", metrics.avgStability);

        // 文档范围
        if (context.getDocumentScope() != null) {
            params.put("documentIds", context.getDocumentScope());
        }

        if (forcedFallback) {
            params.put("forcedFallback", true);
            params.put("fallbackReason", fallbackReason);
        }

        if (context.getExtras() != null) {
            Object recentResponses = context.getExtras().get("recentResponses");
            if (recentResponses != null) {
                params.put("recentResponses", recentResponses);
            }
            Object latestUserAnswer = context.getExtras().get("latestUserAnswer");
            if (latestUserAnswer != null) {
                params.put("latestUserAnswer", latestUserAnswer);
            }
            Object latestFeedback = context.getExtras().get("latestFeedback");
            if (latestFeedback != null) {
                params.put("latestFeedback", latestFeedback);
            }
        }

        return AgentDecision.callTool(
                "QuizGenerator",
                params,
                String.format("生成 %d 道 %s 难度的题目，主题: %s", count, difficulty, topic));
    }

    /**
     * 判断是否应该结束测验
     */
    private boolean shouldFinish(CognitiveMetrics metrics, List<UserKnowledgeState> states) {
        // 条件: D ≥ 70, L ≤ 40, S ≥ 70
        if (metrics.avgDepth < DEPTH_THRESHOLD ||
                metrics.avgLoad > LOAD_THRESHOLD ||
                metrics.avgStability < STABILITY_THRESHOLD) {
            return false;
        }

        // 检查是否所有知识点都已掌握
        if (states != null && !states.isEmpty()) {
            long masteredCount = states.stream()
                    .filter(UserKnowledgeState::isMastered)
                    .count();
            return masteredCount == states.size();
        }

        return false;
    }

    /**
     * 判断用户是否处于挣扎状态
     */
    private boolean isStruggling(CognitiveMetrics metrics) {
        return metrics.avgLoad > 60 && metrics.avgStability < 50;
    }

    /**
     * 确定题目数量
     */
    private int determineQuestionCount(CognitiveMetrics metrics) {
        // 认知负荷高时减少题量
        if (metrics.avgLoad > 60) {
            return 2;
        }
        // 正常情况
        if (metrics.avgLoad > 30) {
            return 3;
        }
        // 负荷低时可以多出一些
        return 5;
    }

    /**
     * 确定难度
     */
    private String determineDifficulty(CognitiveMetrics metrics) {
        // 理解深度高且负荷低，可以提高难度
        if (metrics.avgDepth >= 70 && metrics.avgLoad < 30) {
            return "HARD";
        }
        // 理解深度低或负荷高，降低难度
        if (metrics.avgDepth < 50 || metrics.avgLoad > 60) {
            return "EASY";
        }
        return "MEDIUM";
    }

    /**
     * 确定主题 (找出需要加强的知识点)
     */
    private String determineTopic(List<UserKnowledgeState> states) {
        if (states == null || states.isEmpty()) {
            return "综合知识";
        }

        // 找出稳定性最低的知识点
        return states.stream()
                .filter(s -> s.getStabilityScore() < STABILITY_THRESHOLD)
                .min((a, b) -> a.getStabilityScore() - b.getStabilityScore())
                .map(UserKnowledgeState::getTopicName)
                .orElse(states.get(0).getTopicName());
    }

    /**
     * 计算综合认知指标
     */
    private CognitiveMetrics calculateMetrics(List<UserKnowledgeState> states) {
        CognitiveMetrics metrics = new CognitiveMetrics();

        if (states == null || states.isEmpty()) {
            // 默认值
            metrics.avgDepth = 50;
            metrics.avgLoad = 50;
            metrics.avgStability = 50;
            return metrics;
        }

        int totalDepth = 0, totalLoad = 0, totalStability = 0;
        for (UserKnowledgeState state : states) {
            totalDepth += state.getUnderstandingDepth();
            totalLoad += state.getCognitiveLoadScore();
            totalStability += state.getStabilityScore();
        }

        int count = states.size();
        metrics.avgDepth = totalDepth / count;
        metrics.avgLoad = totalLoad / count;
        metrics.avgStability = totalStability / count;

        return metrics;
    }

    /**
     * 认知指标封装
     */
    private static class CognitiveMetrics {
        int avgDepth;
        int avgLoad;
        int avgStability;
    }
}
