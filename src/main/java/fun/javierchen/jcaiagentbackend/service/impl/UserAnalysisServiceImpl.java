package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.agent.quiz.analyzer.CognitiveAnalyzer;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuestionResponse;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizQuestion;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UnmasteredKnowledge;
import fun.javierchen.jcaiagentbackend.repository.QuestionResponseRepository;
import fun.javierchen.jcaiagentbackend.repository.QuizQuestionRepository;
import fun.javierchen.jcaiagentbackend.repository.QuizSessionRepository;
import fun.javierchen.jcaiagentbackend.repository.UnmasteredKnowledgeRepository;
import fun.javierchen.jcaiagentbackend.service.UserAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户分析服务实现
 *
 * @author JavierChen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAnalysisServiceImpl implements UserAnalysisService {

    private final CognitiveAnalyzer cognitiveAnalyzer;
    private final QuizSessionRepository sessionRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuestionResponseRepository responseRepository;
    private final UnmasteredKnowledgeRepository gapRepository;

    @Override
    public UserCognitiveStateVO getUserCognitiveState(Long tenantId, Long userId) {
        log.info("获取用户认知状态: userId={}", userId);

        CognitiveAnalyzer.CognitiveReport report = cognitiveAnalyzer.analyzeUser(userId);

        // 获取答题统计
        long totalAnswers = responseRepository.countActiveByUserId(userId);
        long correctAnswers = responseRepository.countCorrectByUserId(userId);
        double accuracy = totalAnswers > 0 ? (double) correctAnswers / totalAnswers * 100 : 0;

        // 获取知识缺口数
        long activeGaps = gapRepository.countActiveGapsByUserId(userId);

        // 生成建议
        String recommendation = generateRecommendation(report);

        return UserCognitiveStateVO.builder()
                .userId(userId)
                .avgUnderstandingDepth(report.getAvgDepth())
                .avgCognitiveLoad(report.getAvgLoad())
                .avgStability(report.getAvgStability())
                .masteredCount(report.getMasteredCount())
                .unmasteredCount(report.getUnmasteredCount())
                .totalTopics(report.getMasteredCount() + report.getUnmasteredCount())
                .totalAnswers(totalAnswers)
                .correctAnswers(correctAnswers)
                .accuracy(Math.round(accuracy * 10) / 10.0)
                .strugglingTopics(report.getStrugglingTopics())
                .readyForChallenge(report.getReadyForChallenge())
                .recommendation(recommendation)
                .masteryAchieved(report.isMasteryAchieved())
                .build();
    }

    @Override
    public SessionReportVO getSessionReport(UUID sessionId, Long userId) {
        log.info("获取会话报告: sessionId={}", sessionId);

        QuizSession session = sessionRepository.findWithAllDetailsById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        List<QuestionResponse> responses = responseRepository.findActiveBySessionId(sessionId);
        List<QuizQuestion> questions = questionRepository.findActiveBySessionId(sessionId);

        // 基础统计
        int totalQuestions = questions.size();
        int correctCount = (int) responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
        int totalScore = responses.stream().mapToInt(QuestionResponse::getScore).sum();
        double accuracy = totalQuestions > 0 ? (double) correctCount / totalQuestions * 100 : 0;

        // 响应时间
        Double avgResponseTime = responseRepository.findAvgResponseTimeBySessionId(sessionId);

        // 用时计算
        Long durationSeconds = null;
        if (session.getStartedAt() != null && session.getCompletedAt() != null) {
            durationSeconds = Duration.between(session.getStartedAt(), session.getCompletedAt()).getSeconds();
        }

        // 知识点分析
        Map<String, List<QuestionResponse>> byConceptMap = responses.stream()
                .filter(r -> r.getQuestion() != null && r.getQuestion().getRelatedConcept() != null)
                .collect(Collectors.groupingBy(r -> r.getQuestion().getRelatedConcept()));

        List<SessionReportVO.ConceptAnalysis> conceptAnalyses = byConceptMap.entrySet().stream()
                .map(entry -> {
                    String concept = entry.getKey();
                    List<QuestionResponse> conceptResponses = entry.getValue();
                    int conceptCorrect = (int) conceptResponses.stream()
                            .filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
                    double conceptAccuracy = conceptResponses.isEmpty() ? 0
                            : (double) conceptCorrect / conceptResponses.size() * 100;

                    String mastery = conceptAccuracy >= 70 ? "MASTERED"
                            : conceptAccuracy >= 50 ? "PARTIAL" : "UNMASTERED";

                    return SessionReportVO.ConceptAnalysis.builder()
                            .concept(concept)
                            .questionCount(conceptResponses.size())
                            .correctCount(conceptCorrect)
                            .accuracy(Math.round(conceptAccuracy * 10) / 10.0)
                            .mastery(mastery)
                            .build();
                })
                .toList();

        // 生成建议
        List<String> suggestions = generateSuggestions(accuracy, avgResponseTime, conceptAnalyses);

        return SessionReportVO.builder()
                .sessionId(sessionId)
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .totalScore(totalScore)
                .accuracy(Math.round(accuracy * 10) / 10.0)
                .avgResponseTimeMs(avgResponseTime)
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(durationSeconds)
                .understandingDepth(50) // 简化处理
                .cognitiveLoad(50)
                .stability(50)
                .conceptAnalyses(conceptAnalyses)
                .suggestions(suggestions)
                .build();
    }

    @Override
    public KnowledgeGapListVO getUserKnowledgeGaps(Long userId, KnowledgeGapQueryRequest request) {
        log.info("获取用户知识缺口: userId={}", userId);

        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Page<UnmasteredKnowledge> page = gapRepository.findActiveByUserId(userId, pageable);

        List<KnowledgeGapListVO.KnowledgeGapVO> gaps = page.getContent().stream()
                .map(gap -> KnowledgeGapListVO.KnowledgeGapVO.builder()
                        .id(gap.getId())
                        .conceptName(gap.getConceptName())
                        .gapType(gap.getGapType())
                        .gapDescription(gap.getGapDescription())
                        .rootCause(gap.getRootCause())
                        .severity(gap.getSeverity())
                        .status(gap.getStatus())
                        .failureCount(gap.getFailureCount())
                        .createTime(gap.getCreateTime())
                        .resolvedAt(gap.getResolvedAt())
                        .build())
                .toList();

        long activeCount = gapRepository.countActiveGapsByUserId(userId);
        long resolvedCount = gapRepository.countResolvedGapsByUserId(userId);

        return KnowledgeGapListVO.builder()
                .total(page.getTotalElements())
                .activeCount(activeCount)
                .resolvedCount(resolvedCount)
                .list(gaps)
                .build();
    }

    @Override
    @Transactional
    public boolean markGapAsResolved(UUID gapId, Long userId) {
        UnmasteredKnowledge gap = gapRepository.findActiveById(gapId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识缺口不存在"));

        if (!gap.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        if (gap.getStatus() == KnowledgeGapStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该缺口已解决");
        }

        gap.markAsResolved();
        gapRepository.save(gap);
        return true;
    }

    /**
     * 生成学习建议
     */
    private String generateRecommendation(CognitiveAnalyzer.CognitiveReport report) {
        if (report.isMasteryAchieved()) {
            return "恭喜！您已完全掌握所有知识点，可以挑战更高难度的内容。";
        }

        if (report.getAvgLoad() > 60 && report.getAvgStability() < 50) {
            return "建议放慢学习节奏，从基础知识开始巩固，避免认知超负荷。";
        }

        if (report.getAvgDepth() < 50 && report.getAvgLoad() < 40) {
            return "您的学习状态良好，建议增加解释类题目练习，深化概念理解。";
        }

        if (report.getUnmasteredCount() > 0) {
            return String.format("还有 %d 个知识点需要加强，建议针对薄弱点进行专项练习。",
                    report.getUnmasteredCount());
        }

        return "继续保持当前的学习节奏，稳步提升。";
    }

    /**
     * 生成会话建议
     */
    private List<String> generateSuggestions(double accuracy, Double avgResponseTime,
            List<SessionReportVO.ConceptAnalysis> conceptAnalyses) {
        List<String> suggestions = new ArrayList<>();

        // 正确率建议
        if (accuracy < 50) {
            suggestions.add("正确率偏低，建议复习基础概念后再进行测验");
        } else if (accuracy < 70) {
            suggestions.add("表现不错，继续练习可以进一步提升");
        } else {
            suggestions.add("正确率很高，可以尝试更高难度的题目");
        }

        // 响应时间建议
        if (avgResponseTime != null && avgResponseTime > 60000) {
            suggestions.add("答题时间较长，可以加强对知识点的熟练度");
        }

        // 薄弱知识点建议
        List<String> weakConcepts = conceptAnalyses.stream()
                .filter(a -> a.getAccuracy() < 50)
                .map(SessionReportVO.ConceptAnalysis::getConcept)
                .limit(3)
                .toList();

        if (!weakConcepts.isEmpty()) {
            suggestions.add("重点关注: " + String.join("、", weakConcepts));
        }

        return suggestions;
    }
}
