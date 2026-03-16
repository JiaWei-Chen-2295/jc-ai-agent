package fun.javierchen.jcaiagentbackend.agent.quiz.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Quiz Redis 服务
 * 封装 Quiz Agent 所需的 Redis 读写操作
 * - 概念清单缓存
 * - 答题认知指标写缓冲
 * - 已用 chunk 记录
 *
 * @author JavierChen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration SESSION_TTL = Duration.ofHours(2);

    // ==================== Key 模板 ====================

    private static final String CONCEPTS_KEY = "quiz:session:%s:concepts";
    private static final String META_KEY = "quiz:session:%s:meta";
    private static final String STATE_KEY = "quiz:session:%s:state:%s";
    private static final String USED_CHUNKS_KEY = "quiz:session:%s:usedChunks";

    // ==================== 概念清单操作 ====================

    /**
     * 保存概念清单到 Redis
     */
    public void saveConcepts(String sessionId, Set<String> concepts) {
        String key = String.format(CONCEPTS_KEY, sessionId);
        String metaKey = String.format(META_KEY, sessionId);

        redisTemplate.opsForSet().add(key, concepts.toArray(String[]::new));
        redisTemplate.expire(key, SESSION_TTL);

        // 保存元数据
        Map<String, String> meta = Map.of(
                "totalConcepts", String.valueOf(concepts.size()),
                "createdAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(metaKey, meta);
        redisTemplate.expire(metaKey, SESSION_TTL);

        log.info("概念清单已写入 Redis: sessionId={}, count={}", sessionId, concepts.size());
    }

    /**
     * 获取概念清单
     */
    public Set<String> getConcepts(String sessionId) {
        String key = String.format(CONCEPTS_KEY, sessionId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Set.of();
    }

    /**
     * 获取概念总数
     */
    public long getConceptCount(String sessionId) {
        String key = String.format(CONCEPTS_KEY, sessionId);
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }

    // ==================== 认知指标写缓冲 ====================

    /**
     * 更新认知指标到 Redis (Write-Behind)
     *
     * @param sessionId  会话 ID
     * @param concept    概念名
     * @param depth      理解深度
     * @param load       认知负荷
     * @param stability  稳定性
     * @param isCorrect  是否正确
     */
    public void updateKnowledgeState(String sessionId, String concept,
            int depth, int load, int stability, boolean isCorrect) {
        String key = String.format(STATE_KEY, sessionId, concept);

        Map<String, String> fields = new HashMap<>();
        fields.put("depth", String.valueOf(depth));
        fields.put("load", String.valueOf(load));
        fields.put("stability", String.valueOf(stability));

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.opsForHash().increment(key, "total", 1);
        if (isCorrect) {
            redisTemplate.opsForHash().increment(key, "correct", 1);
        }
        redisTemplate.expire(key, SESSION_TTL);
    }

    /**
     * 获取会话中所有概念的认知指标
     *
     * @return Map<概念名, Map<字段名, 值>>
     */
    public Map<String, Map<String, String>> getAllKnowledgeStates(String sessionId) {
        // 使用 SCAN 模式查找所有 state key
        String pattern = String.format("quiz:session:%s:state:*", sessionId);
        Map<String, Map<String, String>> result = new HashMap<>();

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return result;
        }

        String prefix = String.format(STATE_KEY, sessionId, "");
        for (String key : keys) {
            String concept = key.substring(prefix.length());
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            Map<String, String> stateMap = entries.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue().toString()));
            result.put(concept, stateMap);
        }

        return result;
    }

    /**
     * 检查某个 session 是否有缓冲的认知指标
     */
    public boolean hasBufferedStates(String sessionId) {
        String pattern = String.format("quiz:session:%s:state:*", sessionId);
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null && !keys.isEmpty();
    }

    /**
     * 删除指定概念的缓冲 state key，保留其他 session 数据以便失败重试
     */
    public void deleteKnowledgeStates(String sessionId, Collection<String> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return;
        }
        List<String> keys = concepts.stream()
                .filter(Objects::nonNull)
                .map(concept -> String.format(STATE_KEY, sessionId, concept))
                .toList();
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ==================== 已用 chunk 操作 (Phase 3) ====================

    /**
     * 记录已使用的 chunk ID
     */
    public void addUsedChunks(String sessionId, Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String key = String.format(USED_CHUNKS_KEY, sessionId);
        redisTemplate.opsForSet().add(key, chunkIds.toArray(String[]::new));
        redisTemplate.expire(key, SESSION_TTL);
    }

    /**
     * 获取已使用的 chunk ID 集合
     */
    public Set<String> getUsedChunks(String sessionId) {
        String key = String.format(USED_CHUNKS_KEY, sessionId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Set.of();
    }

    // ==================== 清理 ====================

    /**
     * 清理会话相关的所有 Redis 数据
     */
    public void cleanupSession(String sessionId) {
        String conceptsKey = String.format(CONCEPTS_KEY, sessionId);
        String metaKey = String.format(META_KEY, sessionId);
        String usedChunksKey = String.format(USED_CHUNKS_KEY, sessionId);

        List<String> keysToDelete = new ArrayList<>(List.of(conceptsKey, metaKey, usedChunksKey));

        // 查找所有 state key
        String statePattern = String.format("quiz:session:%s:state:*", sessionId);
        Set<String> stateKeys = redisTemplate.keys(statePattern);
        if (stateKeys != null) {
            keysToDelete.addAll(stateKeys);
        }

        redisTemplate.delete(keysToDelete);
        log.info("已清理 session Redis 数据: sessionId={}, keyCount={}", sessionId, keysToDelete.size());
    }
}
