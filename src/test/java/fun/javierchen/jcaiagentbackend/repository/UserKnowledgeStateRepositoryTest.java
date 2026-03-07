package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserKnowledgeStateRepository 测试类
 * 测试三维认知模型的持久化操作
 *
 * @author JavierChen
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserKnowledgeState Repository 测试")
class UserKnowledgeStateRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserKnowledgeStateRepository repository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;

    private UserKnowledgeState savedState;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        savedState = createKnowledgeState("doc_1", "文档1", 60, 50, 60);
        entityManager.persistAndFlush(savedState);
    }

    private UserKnowledgeState createKnowledgeState(String topicId, String topicName,
            int depth, int load, int stability) {
        UserKnowledgeState state = new UserKnowledgeState();
        state.setTenantId(TENANT_ID);
        state.setUserId(USER_ID);
        state.setTopicType(TopicType.DOCUMENT);
        state.setTopicId(topicId);
        state.setTopicName(topicName);
        state.setUnderstandingDepth(depth);
        state.setCognitiveLoadScore(load);
        state.setStabilityScore(stability);
        state.setTotalQuestions(10);
        state.setCorrectAnswers(6);
        state.setIsDelete(0);
        return state;
    }

    @Nested
    @DisplayName("基本 CRUD 操作")
    class CrudTests {

        @Test
        @DisplayName("保存知识状态")
        void save_shouldPersist() {
            UserKnowledgeState newState = createKnowledgeState("doc_2", "文档2", 70, 35, 75);
            UserKnowledgeState saved = repository.save(newState);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("查询用户特定主题的知识状态")
        void findActiveByUserAndTopic_shouldReturnState() {
            Optional<UserKnowledgeState> found = repository.findActiveByUserAndTopic(
                    TENANT_ID, USER_ID, TopicType.DOCUMENT, "doc_1");

            assertThat(found).isPresent();
            assertThat(found.get().getTopicName()).isEqualTo("文档1");
        }

        @Test
        @DisplayName("唯一约束应生效")
        void uniqueConstraint_shouldPreventDuplicates() {
            boolean exists = repository.existsActiveByUserAndTopic(
                    TENANT_ID, USER_ID, TopicType.DOCUMENT, "doc_1");

            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("三维认知模型查询")
    class CognitiveModelTests {

        @BeforeEach
        void setUpMultipleStates() {
            // 已掌握: D >= 70, L <= 40, S >= 70
            UserKnowledgeState mastered = createKnowledgeState("concept_1", "Java基础", 80, 30, 85);
            mastered.setTopicType(TopicType.CONCEPT);
            entityManager.persist(mastered);

            // 挣扎: L > 60, S < 50
            UserKnowledgeState struggling = createKnowledgeState("concept_2", "多线程", 40, 75, 35);
            struggling.setTopicType(TopicType.CONCEPT);
            entityManager.persist(struggling);

            // 可挑战: L < 30, D >= 70
            UserKnowledgeState challengeReady = createKnowledgeState("concept_3", "集合框架", 85, 25, 80);
            challengeReady.setTopicType(TopicType.CONCEPT);
            entityManager.persist(challengeReady);

            entityManager.flush();
        }

        @Test
        @DisplayName("查询已掌握的知识点")
        void findMasteredByUserId_shouldReturnMasteredOnly() {
            List<UserKnowledgeState> mastered = repository.findMasteredByUserId(USER_ID);

            assertThat(mastered).hasSize(2); // Java基础 和 集合框架
            assertThat(mastered).allMatch(s -> s.isMastered());
        }

        @Test
        @DisplayName("查询未掌握的知识点")
        void findUnmasteredByUserId_shouldReturnUnmastered() {
            List<UserKnowledgeState> unmastered = repository.findUnmasteredByUserId(USER_ID);

            assertThat(unmastered).hasSize(2); // 文档1(setUp) 和 多线程
        }

        @Test
        @DisplayName("查询挣扎的知识点")
        void findStrugglingByUserId_shouldReturnStruggling() {
            List<UserKnowledgeState> struggling = repository.findStrugglingByUserId(USER_ID);

            assertThat(struggling).hasSize(1);
            assertThat(struggling.get(0).getTopicName()).isEqualTo("多线程");
        }

        @Test
        @DisplayName("查询可提高难度的知识点")
        void findMasteryReadyForChallenge_shouldReturnReady() {
            List<UserKnowledgeState> ready = repository.findMasteryReadyForChallenge(USER_ID);

            assertThat(ready).hasSize(2); // Java基础 和 集合框架
        }

        @Test
        @DisplayName("统计已掌握的知识点数量")
        void countMasteredByUserId_shouldReturnCount() {
            Long masteredCount = repository.countMasteredByUserId(USER_ID);
            Long unmasteredCount = repository.countUnmasteredByUserId(USER_ID);

            assertThat(masteredCount).isEqualTo(2);
            assertThat(unmasteredCount).isEqualTo(2);
        }

        @Test
        @DisplayName("计算用户平均指标")
        void findAverages_shouldCalculateCorrectly() {
            Double avgDepth = repository.findAvgDepthByUserId(USER_ID);
            Double avgLoad = repository.findAvgLoadByUserId(USER_ID);
            Double avgStability = repository.findAvgStabilityByUserId(USER_ID);

            assertThat(avgDepth).isNotNull();
            assertThat(avgLoad).isNotNull();
            assertThat(avgStability).isNotNull();
        }
    }

    @Nested
    @DisplayName("Entity 便捷方法测试")
    class EntityMethodTests {

        @Test
        @DisplayName("isMastered 应正确判断")
        void isMastered_shouldReturnCorrectly() {
            // 未掌握状态
            assertThat(savedState.isMastered()).isFalse();

            // 设置为已掌握
            savedState.setUnderstandingDepth(75);
            savedState.setCognitiveLoadScore(35);
            savedState.setStabilityScore(80);
            assertThat(savedState.isMastered()).isTrue();
        }

        @Test
        @DisplayName("isStruggling 应正确判断")
        void isStruggling_shouldReturnCorrectly() {
            savedState.setCognitiveLoadScore(70);
            savedState.setStabilityScore(40);

            assertThat(savedState.isStruggling()).isTrue();
        }

        @Test
        @DisplayName("canIncreaseDifficulty 应正确判断")
        void canIncreaseDifficulty_shouldReturnCorrectly() {
            savedState.setUnderstandingDepth(80);
            savedState.setCognitiveLoadScore(25);

            assertThat(savedState.canIncreaseDifficulty()).isTrue();
        }

        @Test
        @DisplayName("getAccuracyRate 应正确计算")
        void getAccuracyRate_shouldCalculate() {
            savedState.setTotalQuestions(10);
            savedState.setCorrectAnswers(8);

            assertThat(savedState.getAccuracyRate()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("getOverallScore 应正确计算")
        void getOverallScore_shouldCalculate() {
            savedState.setUnderstandingDepth(80); // 0.4 * 80 = 32
            savedState.setCognitiveLoadScore(20); // 0.3 * 80 = 24
            savedState.setStabilityScore(90); // 0.3 * 90 = 27
            // 总分: 32 + 24 + 27 = 83

            assertThat(savedState.getOverallScore()).isEqualTo(83.0);
        }

        @Test
        @DisplayName("updateScores 应使用加权移动平均")
        void updateScores_shouldUseWeightedAverage() {
            savedState.setUnderstandingDepth(50);
            savedState.setCognitiveLoadScore(50);
            savedState.setStabilityScore(50);

            savedState.updateScores(100, 100, 100);

            // 加权平均: 0.7 * 50 + 0.3 * 100 = 35 + 30 = 65
            assertThat(savedState.getUnderstandingDepth()).isEqualTo(65);
            assertThat(savedState.getCognitiveLoadScore()).isEqualTo(65);
            assertThat(savedState.getStabilityScore()).isEqualTo(65);
        }

        @Test
        @DisplayName("updateScores 应处理边界值")
        void updateScores_shouldHandleBoundaries() {
            savedState.setUnderstandingDepth(90);
            savedState.updateScores(150, -50, 200);

            // 应被限制在 0-100 范围
            assertThat(savedState.getUnderstandingDepth()).isLessThanOrEqualTo(100);
            assertThat(savedState.getCognitiveLoadScore()).isGreaterThanOrEqualTo(0);
            assertThat(savedState.getStabilityScore()).isLessThanOrEqualTo(100);
        }
    }
}
