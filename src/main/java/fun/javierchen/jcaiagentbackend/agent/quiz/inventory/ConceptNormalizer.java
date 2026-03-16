package fun.javierchen.jcaiagentbackend.agent.quiz.inventory;

import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 概念名称标准化
 * 将 LLM 生成的概念名映射到概念清单中的标准名称
 * - 优先精确匹配（大小写无关）
 * - 字符串相似度 > 0.7 → 映射到已有名称
 *
 * @author JavierChen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConceptNormalizer {

    private final QuizRedisService quizRedisService;

    private static final double SIMILARITY_THRESHOLD = 0.7;

    /**
     * 标准化概念名
     *
     * @param rawConcept 原始概念名（来自 LLM 或答题评估）
     * @param sessionId  会话 ID（用于获取概念清单）
     * @return 标准化后的概念名
     */
    public String normalize(String rawConcept, String sessionId) {
        if (rawConcept == null || rawConcept.isBlank()) {
            return rawConcept;
        }

        if (sessionId == null) {
            return rawConcept.trim();
        }

        Set<String> knownConcepts;
        try {
            knownConcepts = quizRedisService.getConcepts(sessionId);
        } catch (Exception e) {
            log.debug("获取概念清单失败，跳过标准化: {}", e.getMessage());
            return rawConcept.trim();
        }

        if (knownConcepts.isEmpty()) {
            return rawConcept.trim();
        }

        String trimmed = rawConcept.trim();

        // 1. 精确匹配（大小写无关）
        for (String known : knownConcepts) {
            if (known.equalsIgnoreCase(trimmed)) {
                return known;
            }
        }

        // 2. 字符串相似度匹配
        String bestMatch = null;
        double bestScore = 0;

        for (String known : knownConcepts) {
            double similarity = calculateSimilarity(trimmed.toLowerCase(), known.toLowerCase());
            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = known;
            }
        }

        if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD) {
            log.debug("概念标准化: '{}' → '{}' (相似度={})", trimmed, bestMatch,
                    String.format("%.2f", bestScore));
            return bestMatch;
        }

        return trimmed;
    }

    /**
     * 计算两个字符串的相似度 (基于 Jaccard + 编辑距离的混合算法)
     */
    private double calculateSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        // 包含关系：如果一个包含另一个，给较高分
        if (a.contains(b) || b.contains(a)) {
            double lenRatio = (double) Math.min(a.length(), b.length()) / Math.max(a.length(), b.length());
            return 0.6 + 0.4 * lenRatio;
        }

        // 编辑距离相似度
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        double editSimilarity = 1.0 - (double) distance / maxLen;

        // 字符集 Jaccard 相似度
        double jaccardSimilarity = jaccardSimilarity(a, b);

        // 混合权重: 编辑距离 60% + Jaccard 40%
        return editSimilarity * 0.6 + jaccardSimilarity * 0.4;
    }

    /**
     * Levenshtein 编辑距离
     */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        return dp[a.length()][b.length()];
    }

    /**
     * 字符级 Jaccard 相似度
     */
    private double jaccardSimilarity(String a, String b) {
        Set<Character> setA = a.chars().mapToObj(c -> (char) c).collect(java.util.stream.Collectors.toSet());
        Set<Character> setB = b.chars().mapToObj(c -> (char) c).collect(java.util.stream.Collectors.toSet());

        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;

        return union == 0 ? 0.0 : (double) intersection / union;
    }
}
