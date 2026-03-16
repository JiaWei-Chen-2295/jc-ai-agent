package fun.javierchen.jcaiagentbackend.agent.quiz.cache;

import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.repository.UserKnowledgeStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识状态批量 flush 服务
 * Session 结束时将 Redis 中缓冲的认知指标批量写入 DB
 *
 * @author JavierChen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeStateFlushService {

    private final QuizRedisService quizRedisService;
    private final UserKnowledgeStateRepository knowledgeStateRepository;

    /**
     * 将 Redis 中缓冲的认知指标批量 flush 到 DB
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     */
    @Transactional
    public void flush(String sessionId, Long tenantId, Long userId) {
        log.info("开始 flush 认知指标: sessionId={}", sessionId);

        Map<String, Map<String, String>> allStates = quizRedisService.getAllKnowledgeStates(sessionId);
        if (allStates.isEmpty()) {
            log.info("无缓冲的认知指标需要 flush: sessionId={}", sessionId);
            return;
        }

        int flushedCount = 0;
        List<String> flushedConcepts = new ArrayList<>();
        boolean hasFailure = false;
        for (Map.Entry<String, Map<String, String>> entry : allStates.entrySet()) {
            String concept = entry.getKey();
            Map<String, String> stateMap = entry.getValue();

            try {
                int depth = parseIntOrDefault(stateMap.get("depth"), 50);
                int load = parseIntOrDefault(stateMap.get("load"), 50);
                int stability = parseIntOrDefault(stateMap.get("stability"), 50);
                int total = parseIntOrDefault(stateMap.get("total"), 0);
                int correct = parseIntOrDefault(stateMap.get("correct"), 0);

                // 查找或创建 DB 中的知识状态
                UserKnowledgeState dbState = knowledgeStateRepository
                        .findActiveByUserAndTopic(tenantId, userId, TopicType.CONCEPT, concept)
                        .orElseGet(() -> {
                            log.debug("flush 创建新知识状态: concept={}", concept);
                            UserKnowledgeState newState = new UserKnowledgeState();
                            newState.setTenantId(tenantId);
                            newState.setUserId(userId);
                            newState.setTopicType(TopicType.CONCEPT);
                            newState.setTopicId(concept);
                            newState.setTopicName(concept);
                            return newState;
                        });

                // 更新三维指标（直接使用 Redis 中的最终值）
                dbState.updateScores(depth, load, stability);
                dbState.setTotalQuestions(dbState.getTotalQuestions() + total);
                dbState.setCorrectAnswers(dbState.getCorrectAnswers() + correct);

                knowledgeStateRepository.save(dbState);
                flushedCount++;
                flushedConcepts.add(concept);
                log.debug("flush 概念完成: concept={}, D={}, L={}, S={}, total={}, correct={}",
                        concept, depth, load, stability, total, correct);
            } catch (Exception e) {
                hasFailure = true;
                log.error("flush 概念 {} 失败: {}", concept, e.getMessage());
            }
        }

        registerCleanupAfterCommit(sessionId, flushedConcepts, hasFailure);

        log.info("认知指标 flush 完成: sessionId={}, 概念数={}", sessionId, flushedCount);
    }

    private void registerCleanupAfterCommit(String sessionId, List<String> flushedConcepts, boolean hasFailure) {
        if (flushedConcepts.isEmpty() && hasFailure) {
            log.warn("flush 未成功落库任何概念，保留全部 Redis 缓冲待重试: sessionId={}", sessionId);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupBufferedStates(sessionId, flushedConcepts, hasFailure);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupBufferedStates(sessionId, flushedConcepts, hasFailure);
            }
        });
    }

    private void cleanupBufferedStates(String sessionId, List<String> flushedConcepts, boolean hasFailure) {
        if (hasFailure) {
            quizRedisService.deleteKnowledgeStates(sessionId, flushedConcepts);
            log.warn("flush 部分成功，仅清理已落库概念缓冲，保留失败项待重试: sessionId={}, flushed={}",
                    sessionId, flushedConcepts.size());
            return;
        }
        quizRedisService.cleanupSession(sessionId);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
