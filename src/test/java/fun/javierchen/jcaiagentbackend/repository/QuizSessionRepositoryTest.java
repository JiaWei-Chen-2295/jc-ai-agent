package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuizSessionRepository 测试类
 * 测试测验会话的持久化操作
 *
 * @author JavierChen
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("QuizSession Repository 测试")
class QuizSessionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QuizSessionRepository repository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;

    private QuizSession savedSession;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        repository.deleteAll();

        // 创建测试会话
        savedSession = createTestSession(USER_ID, QuizStatus.IN_PROGRESS, QuizMode.ADAPTIVE);
        entityManager.persistAndFlush(savedSession);
    }

    private QuizSession createTestSession(Long userId, QuizStatus status, QuizMode mode) {
        QuizSession session = new QuizSession();
        session.setTenantId(TENANT_ID);
        session.setUserId(userId);
        session.setQuizMode(mode);
        session.setStatus(status);
        session.setDocumentScope(Arrays.asList(1L, 2L, 3L));
        session.setCurrentQuestionNo(0);
        session.setTotalQuestions(10);
        session.setScore(0);
        session.setStartedAt(LocalDateTime.now());
        session.setIsDelete(0);
        return session;
    }

    @Nested
    @DisplayName("基本 CRUD 操作")
    class CrudTests {

        @Test
        @DisplayName("保存会话应自动生成 UUID")
        void save_shouldGenerateUUID() {
            QuizSession newSession = createTestSession(USER_ID, QuizStatus.IN_PROGRESS, QuizMode.EASY);
            QuizSession saved = repository.save(newSession);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getId()).isInstanceOf(UUID.class);
        }

        @Test
        @DisplayName("保存会话应自动填充创建时间")
        void save_shouldPopulateCreateTime() {
            QuizSession newSession = createTestSession(USER_ID, QuizStatus.IN_PROGRESS, QuizMode.EASY);
            QuizSession saved = repository.save(newSession);
            entityManager.flush();

            assertThat(saved.getCreateTime()).isNotNull();
            assertThat(saved.getUpdateTime()).isNotNull();
        }

        @Test
        @DisplayName("根据ID查询会话")
        void findById_shouldReturnSession() {
            Optional<QuizSession> found = repository.findById(savedSession.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("更新会话状态")
        void update_shouldModifyStatus() {
            savedSession.setStatus(QuizStatus.COMPLETED);
            savedSession.setCompletedAt(LocalDateTime.now());
            savedSession.setScore(80);

            QuizSession updated = repository.save(savedSession);
            entityManager.flush();
            entityManager.clear();

            QuizSession reloaded = repository.findById(updated.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(QuizStatus.COMPLETED);
            assertThat(reloaded.getScore()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("按用户查询")
    class FindByUserTests {

        @Test
        @DisplayName("查询用户的活跃会话")
        void findActiveByUserId_shouldReturnUserSessions() {
            List<QuizSession> sessions = repository.findActiveByUserId(USER_ID);

            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("查询用户指定状态的会话")
        void findActiveByUserIdAndStatus_shouldFilterByStatus() {
            // 创建已完成的会话
            QuizSession completed = createTestSession(USER_ID, QuizStatus.COMPLETED, QuizMode.MEDIUM);
            entityManager.persistAndFlush(completed);

            List<QuizSession> inProgressSessions = repository.findActiveByUserIdAndStatus(USER_ID,
                    QuizStatus.IN_PROGRESS);
            List<QuizSession> completedSessions = repository.findActiveByUserIdAndStatus(USER_ID, QuizStatus.COMPLETED);

            assertThat(inProgressSessions).hasSize(1);
            assertThat(completedSessions).hasSize(1);
        }

        @Test
        @DisplayName("分页查询用户的会话")
        void findActiveByUserId_withPageable_shouldReturnPage() {
            // 创建多个会话
            for (int i = 0; i < 15; i++) {
                entityManager.persist(createTestSession(USER_ID, QuizStatus.COMPLETED, QuizMode.EASY));
            }
            entityManager.flush();

            Page<QuizSession> page = repository.findActiveByUserId(USER_ID, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(16); // 包括 setUp 中的
            assertThat(page.getContent()).hasSize(10);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("统计查询")
    class StatisticsTests {

        @Test
        @DisplayName("统计用户的总会话数")
        void countActiveByUserId_shouldReturnCount() {
            long count = repository.countActiveByUserId(USER_ID);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("统计已完成的会话数")
        void countCompletedByUserId_shouldReturnCompletedCount() {
            // 创建已完成的会话
            QuizSession completed1 = createTestSession(USER_ID, QuizStatus.COMPLETED, QuizMode.EASY);
            QuizSession completed2 = createTestSession(USER_ID, QuizStatus.COMPLETED, QuizMode.MEDIUM);
            entityManager.persist(completed1);
            entityManager.persist(completed2);
            entityManager.flush();

            long completedCount = repository.countCompletedByUserId(USER_ID);
            long inProgressCount = repository.countInProgressByUserId(USER_ID);

            assertThat(completedCount).isEqualTo(2);
            assertThat(inProgressCount).isEqualTo(1);
        }

        @Test
        @DisplayName("检查用户是否有进行中的会话")
        void hasInProgressSession_shouldReturnTrue() {
            boolean hasInProgress = repository.hasInProgressSession(USER_ID);

            assertThat(hasInProgress).isTrue();
        }

        @Test
        @DisplayName("用户无进行中会话时应返回 false")
        void hasInProgressSession_shouldReturnFalse_whenNoInProgress() {
            savedSession.setStatus(QuizStatus.COMPLETED);
            repository.save(savedSession);
            entityManager.flush();

            boolean hasInProgress = repository.hasInProgressSession(USER_ID);

            assertThat(hasInProgress).isFalse();
        }
    }

    @Nested
    @DisplayName("软删除测试")
    class SoftDeleteTests {

        @Test
        @DisplayName("软删除后不应查到")
        void findActiveById_shouldNotReturnDeleted() {
            savedSession.setIsDelete(1);
            repository.save(savedSession);
            entityManager.flush();

            Optional<QuizSession> found = repository.findActiveById(savedSession.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("软删除不影响原始 findById")
        void findById_shouldStillReturnDeleted() {
            savedSession.setIsDelete(1);
            repository.save(savedSession);
            entityManager.flush();

            Optional<QuizSession> found = repository.findById(savedSession.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getIsDelete()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Entity 便捷方法测试")
    class EntityMethodTests {

        @Test
        @DisplayName("canContinue 应正确判断是否可继续")
        void canContinue_shouldReturnCorrectly() {
            savedSession.setStatus(QuizStatus.IN_PROGRESS);
            assertThat(savedSession.canContinue()).isTrue();

            savedSession.setStatus(QuizStatus.PAUSED);
            assertThat(savedSession.canContinue()).isTrue();

            savedSession.setStatus(QuizStatus.COMPLETED);
            assertThat(savedSession.canContinue()).isFalse();
        }

        @Test
        @DisplayName("isFinished 应正确判断是否已结束")
        void isFinished_shouldReturnCorrectly() {
            savedSession.setStatus(QuizStatus.IN_PROGRESS);
            assertThat(savedSession.isFinished()).isFalse();

            savedSession.setStatus(QuizStatus.COMPLETED);
            assertThat(savedSession.isFinished()).isTrue();

            savedSession.setStatus(QuizStatus.ABANDONED);
            assertThat(savedSession.isFinished()).isTrue();
        }

        @Test
        @DisplayName("getAccuracyRate 应正确计算正确率")
        void getAccuracyRate_shouldCalculateCorrectly() {
            savedSession.setTotalQuestions(10);
            savedSession.setScore(8);

            assertThat(savedSession.getAccuracyRate()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("getAccuracyRate 应处理零题目")
        void getAccuracyRate_shouldHandleZeroQuestions() {
            savedSession.setTotalQuestions(0);
            savedSession.setScore(0);

            assertThat(savedSession.getAccuracyRate()).isEqualTo(0.0);
        }
    }
}
