package fun.javierchen.jcaiagentbackend.agent.quiz.analyzer;

import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuestionResponse;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.repository.QuestionResponseRepository;
import fun.javierchen.jcaiagentbackend.repository.UserKnowledgeStateRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 认知分析器
 * 实现三维认知模型的计算
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CognitiveAnalyzer {

    private final UserKnowledgeStateRepository knowledgeStateRepository;
    private final QuestionResponseRepository responseRepository;

    // 权重配置 (可通过配置文件调优)
    private static final double W_CHOICE_ACCURACY = 0.3;
    private static final double W_EXPLANATION_QUALITY = 0.4;
    private static final double W_REASONING_SCORE = 0.3;

    private static final double W_RESPONSE_TIME = 0.5;
    private static final double W_HESITATION = 0.3;
    private static final double W_ABANDON_RATE = 0.2;

    /**
     * 计算理解深度 (Understanding Depth)
     * D = w1 * 选择题正确率 + w2 * 解释题质量分 + w3 * 关联推理分
     */
    public int calculateUnderstandingDepth(Long userId, String concept) {
        long correct = responseRepository.countCorrectByUserIdAndConcept(userId, concept);
        long total = responseRepository.countByUserIdAndConcept(userId, concept);

        if (total == 0) {
            return 50; // 默认值
        }

        double choiceAccuracy = (double) correct / total * 100;

        // 简化处理: 假设解释题和推理题也用正确率近似
        double explanationQuality = choiceAccuracy * 0.9; // 略低于选择题
        double reasoningScore = choiceAccuracy * 0.85;

        int depth = (int) (W_CHOICE_ACCURACY * choiceAccuracy
                + W_EXPLANATION_QUALITY * explanationQuality
                + W_REASONING_SCORE * reasoningScore);

        return Math.max(0, Math.min(100, depth));
    }

    /**
     * 计算认知负荷 (Cognitive Load)
     * L = w1 * 响应时间系数 + w2 * 犹豫系数 + w3 * 放弃率
     */
    public int calculateCognitiveLoad(Long userId, List<QuestionResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return 50;
        }

        // 计算平均响应时间
        Double avgTime = responseRepository.findAvgResponseTimeByUserId(userId);
        if (avgTime == null)
            avgTime = 30000.0;

        // 响应时间系数
        double totalTimeCoeff = 0;
        int hesitationCount = 0;
        int abandonCount = 0;

        for (QuestionResponse resp : responses) {
            double timeCoeff = Math.min(100, (resp.getResponseTimeMs() / avgTime) * 50);
            totalTimeCoeff += timeCoeff;

            if (Boolean.TRUE.equals(resp.getHesitationDetected())) {
                hesitationCount++;
            }
        }

        double avgTimeCoeff = totalTimeCoeff / responses.size();
        double hesitationCoeff = hesitationCount * 10.0;
        double abandonRate = (double) abandonCount / responses.size() * 100;

        int load = (int) (W_RESPONSE_TIME * avgTimeCoeff
                + W_HESITATION * hesitationCoeff
                + W_ABANDON_RATE * abandonRate);

        return Math.max(0, Math.min(100, load));
    }

    /**
     * 计算稳定性 (Stability)
     * S = 100 - (重复错误数 / 同概念总测试数) * 100
     */
    public int calculateStability(Long userId, String concept) {
        List<QuestionResponse> responses = responseRepository.findByUserIdAndConcept(userId, concept);

        if (responses.isEmpty()) {
            return 50;
        }

        // 统计连续错误
        int repeatErrors = 0;
        boolean lastWrong = false;

        for (QuestionResponse resp : responses) {
            if (!Boolean.TRUE.equals(resp.getIsCorrect())) {
                if (lastWrong) {
                    repeatErrors++;
                }
                lastWrong = true;
            } else {
                lastWrong = false;
            }
        }

        double errorRate = (double) repeatErrors / responses.size();
        int stability = (int) (100 - errorRate * 100);

        return Math.max(0, Math.min(100, stability));
    }

    /**
     * 更新用户知识状态
     */
    public UserKnowledgeState updateKnowledgeState(Long tenantId, Long userId,
            String concept, QuestionResponse latestResponse) {

        // 查找或创建知识状态
        UserKnowledgeState state = knowledgeStateRepository
                .findActiveByUserAndTopic(tenantId, userId, TopicType.CONCEPT, concept)
                .orElseGet(() -> {
                    UserKnowledgeState newState = new UserKnowledgeState();
                    newState.setTenantId(tenantId);
                    newState.setUserId(userId);
                    newState.setTopicType(TopicType.CONCEPT);
                    newState.setTopicId(concept);
                    newState.setTopicName(concept);
                    return newState;
                });

        // 更新统计
        state.setTotalQuestions(state.getTotalQuestions() + 1);
        if (Boolean.TRUE.equals(latestResponse.getIsCorrect())) {
            state.setCorrectAnswers(state.getCorrectAnswers() + 1);
        }

        // 获取最近的回答记录
        List<QuestionResponse> recentResponses = responseRepository
                .findByUserIdAndConcept(userId, concept);

        // 重新计算三维指标
        int newDepth = calculateUnderstandingDepth(userId, concept);
        int newLoad = calculateCognitiveLoad(userId, recentResponses);
        int newStability = calculateStability(userId, concept);

        // 使用加权移动平均更新
        state.updateScores(newDepth, newLoad, newStability);

        return knowledgeStateRepository.save(state);
    }

    /**
     * 分析用户整体认知状态
     */
    public CognitiveReport analyzeUser(Long userId) {
        List<UserKnowledgeState> states = knowledgeStateRepository.findActiveByUserId(userId);

        if (states.isEmpty()) {
            return CognitiveReport.builder()
                    .userId(userId)
                    .avgDepth(50)
                    .avgLoad(50)
                    .avgStability(50)
                    .masteredCount(0)
                    .unmasteredCount(0)
                    .build();
        }

        Double avgDepth = knowledgeStateRepository.findAvgDepthByUserId(userId);
        Double avgLoad = knowledgeStateRepository.findAvgLoadByUserId(userId);
        Double avgStability = knowledgeStateRepository.findAvgStabilityByUserId(userId);
        Long masteredCount = knowledgeStateRepository.countMasteredByUserId(userId);
        Long unmasteredCount = knowledgeStateRepository.countUnmasteredByUserId(userId);

        List<UserKnowledgeState> strugglingTopics = knowledgeStateRepository.findStrugglingByUserId(userId);
        List<UserKnowledgeState> readyForChallenge = knowledgeStateRepository.findMasteryReadyForChallenge(userId);

        return CognitiveReport.builder()
                .userId(userId)
                .avgDepth(avgDepth != null ? avgDepth.intValue() : 50)
                .avgLoad(avgLoad != null ? avgLoad.intValue() : 50)
                .avgStability(avgStability != null ? avgStability.intValue() : 50)
                .masteredCount(masteredCount != null ? masteredCount.intValue() : 0)
                .unmasteredCount(unmasteredCount != null ? unmasteredCount.intValue() : 0)
                .strugglingTopics(strugglingTopics.stream()
                        .map(UserKnowledgeState::getTopicName)
                        .toList())
                .readyForChallenge(readyForChallenge.stream()
                        .map(UserKnowledgeState::getTopicName)
                        .toList())
                .build();
    }

    /**
     * 判断概念掌握程度
     */
    public ConceptMastery determineConceptMastery(int depth, int load, int stability) {
        if (depth >= 70 && load <= 40 && stability >= 70) {
            return ConceptMastery.MASTERED;
        } else if (depth >= 50 && stability >= 50) {
            return ConceptMastery.PARTIAL;
        } else {
            return ConceptMastery.UNMASTERED;
        }
    }

    /**
     * 认知报告
     */
    @Data
    @Builder
    public static class CognitiveReport {
        private Long userId;
        private int avgDepth;
        private int avgLoad;
        private int avgStability;
        private int masteredCount;
        private int unmasteredCount;
        private List<String> strugglingTopics;
        private List<String> readyForChallenge;

        public boolean isMasteryAchieved() {
            return avgDepth >= 70 && avgLoad <= 40 && avgStability >= 70 && unmasteredCount == 0;
        }
    }
}
