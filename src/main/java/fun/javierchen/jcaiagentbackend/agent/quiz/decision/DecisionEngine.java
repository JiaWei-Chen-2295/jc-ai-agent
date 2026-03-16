package fun.javierchen.jcaiagentbackend.agent.quiz.decision;

import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentContext;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentDecision;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentState;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.rag.config.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 决策引擎
 * 根据用户状态和上下文做出智能决策
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionEngine {

    private final VectorStoreService vectorStoreService;
    private final QuizRedisService quizRedisService;

    /**
     * 状态指标的达标阈值
     */
    private static final int DEPTH_THRESHOLD = 70;
    private static final int LOAD_THRESHOLD = 40;
    private static final int STABILITY_THRESHOLD = 70;

    /**
     * 最低答题数门槛：已答题数 < 此值时永不结束
     */
    private static final int MIN_QUESTIONS_BEFORE_FINISH = 8;

    /**
     * 覆盖率下限：已测概念数 / 估算概念总数 < 此值时不结束
     */
    private static final double MIN_COVERAGE_RATIO = 0.5;

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

        // 判断是否已完成（传入 session 做覆盖率检查）
        if (shouldFinish(metrics, knowledgeStates, context.getSession())) {
            log.info("决策: 结束测验 — 所有守卫通过, D={}, L={}, S={}",
                    metrics.avgDepth, metrics.avgLoad, metrics.avgStability);
            return AgentDecision.finish("用户已掌握全部内容");
        }

        // 判断是否需要降级
        if (isStruggling(metrics)) {
            log.info("决策: 触发降级 — 认知负荷过高 load={}, stability={}",
                    metrics.avgLoad, metrics.avgStability);
            return buildQuestionGenerationDecision(context, metrics, knowledgeStates,
                    true, "用户认知负荷过高，需降级为更基础、更缓和的题目");
        }

        // 决定生成题目
        return decideForQuestionGeneration(context, metrics, knowledgeStates);
    }

    /**
     * 判断是否应该结束测验 (公开方法，供外部调用)
     * 结束条件: D ≥ 70, L ≤ 40, S ≥ 70 且所有知识点已掌握，且满足覆盖率守卫
     *
     * @param knowledgeStates 用户知识状态列表
     * @param session         当前测验会话（用于覆盖率检查）
     * @return 是否应该结束测验
     */
    public boolean checkShouldFinish(List<UserKnowledgeState> knowledgeStates, QuizSession session) {
        CognitiveMetrics metrics = calculateMetrics(knowledgeStates);
        return shouldFinish(metrics, knowledgeStates, session);
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
        String topic = determineTopic(knowledgeStates, context.getSession());
        params.put("topic", topic);

        // 传递认知状态
        params.put("understandingDepth", metrics.avgDepth);
        params.put("cognitiveLoad", metrics.avgLoad);
        params.put("stability", metrics.avgStability);

        // 文档范围
        if (context.getDocumentScope() != null) {
            params.put("documentIds", context.getDocumentScope());
        }

        if (context.getTenantId() != null) {
            params.put("tenantId", context.getTenantId());
        }

        // Phase 3: 传递 sessionId 用于 chunk 去重
        if (context.getSessionId() != null) {
            params.put("sessionId", context.getSessionId());
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
     * Phase 1 覆盖率守卫 + Phase 2 概念清单精确检查
     */
    private boolean shouldFinish(CognitiveMetrics metrics, List<UserKnowledgeState> states,
            QuizSession session) {
        // 守卫 1：最低题数门槛
        if (session != null) {
            int answeredCount = session.getCurrentQuestionNo();
            if (answeredCount < MIN_QUESTIONS_BEFORE_FINISH) {
                log.debug("最低题数守卫: 已答{}题 < 门槛{}题，不结束",
                        answeredCount, MIN_QUESTIONS_BEFORE_FINISH);
                return false;
            }
        }

        // 条件: D ≥ 70, L ≤ 40, S ≥ 70
        if (metrics.avgDepth < DEPTH_THRESHOLD ||
                metrics.avgLoad > LOAD_THRESHOLD ||
                metrics.avgStability < STABILITY_THRESHOLD) {
            log.debug("认知指标未达标，不结束: D={}(<{}?) L={}(>{}?) S={}(<{}?)",
                    metrics.avgDepth, DEPTH_THRESHOLD,
                    metrics.avgLoad, LOAD_THRESHOLD,
                    metrics.avgStability, STABILITY_THRESHOLD);
            return false;
        }

        // 检查是否所有已测知识点都已掌握
        if (states == null || states.isEmpty()) {
            log.debug("无已测知识点，不结束");
            return false;
        }
        long masteredCount = states.stream()
                .filter(UserKnowledgeState::isMastered)
                .count();
        if (masteredCount < states.size()) {
            log.debug("存在未掌握知识点: mastered={}/{}", masteredCount, states.size());
            return false;
        }

        // 守卫 2：覆盖率检查（优先用 Redis 概念清单，降级用 chunk 估算）
        int estimatedConcepts = estimateConceptCount(session);
        if (estimatedConcepts > 0) {
            double coverage = (double) states.size() / estimatedConcepts;
            if (coverage < MIN_COVERAGE_RATIO) {
                log.info("覆盖率守卫: 已测{}/估算{}个概念, 覆盖率{}% < 门槛{}%，不结束",
                        states.size(), estimatedConcepts,
                        String.format("%.1f", coverage * 100), (int) (MIN_COVERAGE_RATIO * 100));
                return false;
            }
        }

        return true;
    }

    /**
     * 估算知识库中的概念数量
     * 优先从 Redis 概念清单获取精确值，降级使用 chunk 数 / 3
     */
    private int estimateConceptCount(QuizSession session) {
        if (session == null) {
            return 0;
        }

        // 优先: 从 Redis 读取概念清单总数 (Phase 2)
        String sessionId = session.getId().toString();
        try {
            long redisConcepts = quizRedisService.getConceptCount(sessionId);
            if (redisConcepts > 0) {
                log.debug("概念数来源: Redis, count={}", redisConcepts);
                return (int) redisConcepts;
            }
        } catch (Exception e) {
            log.debug("Redis 读取概念数失败，降级为 chunk 估算: {}", e.getMessage());
        }

        // 降级: agentState 中存储的概念清单
        if (session.getAgentState() != null) {
            Object concepts = session.getAgentState().get("concepts");
            if (concepts instanceof Collection<?> col && !col.isEmpty()) {
                log.debug("概念数来源: agentState, count={}", col.size());
                return col.size();
            }
        }

        // 最终降级: chunk 总数 / 3 估算
        List<Long> documentIds = session.getDocumentScope();
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        try {
            int totalChunks = 0;
            for (Long docId : documentIds) {
                totalChunks += vectorStoreService.countByDocumentId(docId, session.getTenantId());
            }
            return Math.max(1, totalChunks / 3);
        } catch (Exception e) {
            log.warn("概念数来源: chunk估算失败: {}", e.getMessage());
            return 0;
        }
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
     * 确定主题 (找出需要加强或未覆盖的知识点)
     * Phase 2: 优先从 Redis 概念清单中选取未覆盖的概念
     */
    private String determineTopic(List<UserKnowledgeState> states, QuizSession session) {
        // 优先: 从概念清单中选未覆盖的概念
        if (session != null) {
            String sessionId = session.getId().toString();
            try {
                Set<String> allConcepts = quizRedisService.getConcepts(sessionId);
                if (allConcepts != null && !allConcepts.isEmpty()) {
                    Set<String> testedConcepts = states == null ? Set.of()
                            : states.stream()
                                    .map(UserKnowledgeState::getTopicName)
                                    .collect(Collectors.toSet());

                    // 找未测概念
                    List<String> untestedConcepts = allConcepts.stream()
                            .filter(c -> !testedConcepts.contains(c))
                            .toList();
                    if (!untestedConcepts.isEmpty()) {
                        // 随机选一个未测概念，增加多样性
                        String selected = untestedConcepts.get(
                                new Random().nextInt(untestedConcepts.size()));
                        log.debug("从概念清单中选取未覆盖概念: {}", selected);
                        return selected;
                    }
                }
            } catch (Exception e) {
                log.debug("Redis 读取概念清单失败，降级使用已有 state: {}", e.getMessage());
            }

            // 降级: 从 agentState 中读取概念清单
            if (session.getAgentState() != null) {
                Object concepts = session.getAgentState().get("concepts");
                if (concepts instanceof Collection<?> col && !col.isEmpty()) {
                    Set<String> testedConcepts = states == null ? Set.of()
                            : states.stream()
                                    .map(UserKnowledgeState::getTopicName)
                                    .collect(Collectors.toSet());
                    List<String> untestedConcepts = col.stream()
                            .map(Object::toString)
                            .filter(c -> !testedConcepts.contains(c))
                            .toList();
                    if (!untestedConcepts.isEmpty()) {
                        String selected = untestedConcepts.get(new Random().nextInt(untestedConcepts.size()));
                        log.debug("从 agentState 概念清单中选取未覆盖概念: {}", selected);
                        return selected;
                    }
                }
            }
        }

        // 降级: 从已有 state 中找稳定性最低的知识点
        if (states == null || states.isEmpty()) {
            log.debug("无已有知识状态且无概念清单，使用通用主题");
            return "综合知识";
        }

        String topic = states.stream()
                .filter(s -> s.getStabilityScore() < STABILITY_THRESHOLD)
                .min((a, b) -> a.getStabilityScore() - b.getStabilityScore())
                .map(UserKnowledgeState::getTopicName)
                .orElse(states.get(0).getTopicName());
        log.debug("从已有 state 中选取最弱知识点: {}", topic);
        return topic;
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


