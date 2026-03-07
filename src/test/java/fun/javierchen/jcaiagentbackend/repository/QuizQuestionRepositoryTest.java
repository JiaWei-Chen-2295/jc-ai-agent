package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizQuestion;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuizQuestionRepository 测试类
 * 测试测验题目的持久化操作
 *
 * @author JavierChen
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("QuizQuestion Repository 测试")
class QuizQuestionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private QuizSessionRepository sessionRepository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;

    private QuizSession testSession;
    private QuizQuestion savedQuestion;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        questionRepository.deleteAll();
        sessionRepository.deleteAll();

        // 创建测试会话
        testSession = createTestSession();
        entityManager.persistAndFlush(testSession);

        // 创建测试题目
        savedQuestion = createTestQuestion(testSession, 1, QuestionType.SINGLE_CHOICE, Difficulty.EASY);
        entityManager.persistAndFlush(savedQuestion);
    }

    private QuizSession createTestSession() {
        QuizSession session = new QuizSession();
        session.setTenantId(TENANT_ID);
        session.setUserId(USER_ID);
        session.setQuizMode(QuizMode.ADAPTIVE);
        session.setStatus(QuizStatus.IN_PROGRESS);
        session.setCurrentQuestionNo(0);
        session.setTotalQuestions(10);
        session.setIsDelete(0);
        return session;
    }

    private QuizQuestion createTestQuestion(QuizSession session, int questionNo,
            QuestionType type, Difficulty difficulty) {
        QuizQuestion question = new QuizQuestion();
        question.setSession(session);
        question.setTenantId(TENANT_ID);
        question.setQuestionNo(questionNo);
        question.setQuestionText("这是测试题目 " + questionNo);
        question.setQuestionType(type);
        question.setDifficulty(difficulty);
        question.setOptions(Arrays.asList("A", "B", "C", "D"));
        question.setCorrectAnswer("A");
        question.setExplanation("正确答案解释");
        question.setRelatedConcept("测试概念");
        question.setIsDelete(0);
        return question;
    }

    @Nested
    @DisplayName("基本 CRUD 操作")
    class CrudTests {

        @Test
        @DisplayName("保存题目应自动生成 UUID")
        void save_shouldGenerateUUID() {
            QuizQuestion newQuestion = createTestQuestion(testSession, 2, QuestionType.TRUE_FALSE, Difficulty.EASY);
            QuizQuestion saved = questionRepository.save(newQuestion);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getId()).isInstanceOf(UUID.class);
        }

        @Test
        @DisplayName("根据ID查询题目")
        void findActiveById_shouldReturnQuestion() {
            Optional<QuizQuestion> found = questionRepository.findActiveById(savedQuestion.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getQuestionNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("更新题目难度")
        void update_shouldModifyDifficulty() {
            savedQuestion.setDifficulty(Difficulty.HARD);
            questionRepository.save(savedQuestion);
            entityManager.flush();
            entityManager.clear();

            QuizQuestion reloaded = questionRepository.findById(savedQuestion.getId()).orElseThrow();
            assertThat(reloaded.getDifficulty()).isEqualTo(Difficulty.HARD);
        }
    }

    @Nested
    @DisplayName("按会话查询")
    class FindBySessionTests {

        @Test
        @DisplayName("查询会话的所有题目")
        void findActiveBySessionId_shouldReturnQuestions() {
            // 添加更多题目
            for (int i = 2; i <= 5; i++) {
                entityManager
                        .persist(createTestQuestion(testSession, i, QuestionType.SINGLE_CHOICE, Difficulty.MEDIUM));
            }
            entityManager.flush();

            List<QuizQuestion> questions = questionRepository.findActiveBySessionId(testSession.getId());

            assertThat(questions).hasSize(5);
            // 验证按题号排序
            assertThat(questions.get(0).getQuestionNo()).isEqualTo(1);
            assertThat(questions.get(4).getQuestionNo()).isEqualTo(5);
        }

        @Test
        @DisplayName("查询指定题号的题目")
        void findActiveBySessionIdAndQuestionNo_shouldReturnSpecificQuestion() {
            Optional<QuizQuestion> found = questionRepository.findActiveBySessionIdAndQuestionNo(testSession.getId(),
                    1);

            assertThat(found).isPresent();
            assertThat(found.get().getQuestionNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("获取会话的最大题号")
        void findMaxQuestionNoBySessionId_shouldReturnMax() {
            // 添加更多题目
            for (int i = 2; i <= 10; i++) {
                entityManager.persist(createTestQuestion(testSession, i, QuestionType.FILL_IN_BLANK, Difficulty.HARD));
            }
            entityManager.flush();

            Integer maxNo = questionRepository.findMaxQuestionNoBySessionId(testSession.getId());

            assertThat(maxNo).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("按类型和难度查询")
    class FindByTypeAndDifficultyTests {

        @BeforeEach
        void setUpMultipleQuestions() {
            // 创建不同类型和难度的题目
            entityManager.persist(createTestQuestion(testSession, 2, QuestionType.TRUE_FALSE, Difficulty.EASY));
            entityManager.persist(createTestQuestion(testSession, 3, QuestionType.FILL_IN_BLANK, Difficulty.MEDIUM));
            entityManager.persist(createTestQuestion(testSession, 4, QuestionType.EXPLANATION, Difficulty.HARD));
            entityManager.persist(createTestQuestion(testSession, 5, QuestionType.SINGLE_CHOICE, Difficulty.HARD));
            entityManager.flush();
        }

        @Test
        @DisplayName("按题型查询")
        void findBySessionIdAndQuestionType_shouldFilterByType() {
            List<QuizQuestion> singleChoice = questionRepository.findBySessionIdAndQuestionTypeAndIsDelete(
                    testSession.getId(), QuestionType.SINGLE_CHOICE, 0);

            assertThat(singleChoice).hasSize(2);
        }

        @Test
        @DisplayName("按难度查询")
        void findBySessionIdAndDifficulty_shouldFilterByDifficulty() {
            List<QuizQuestion> hardQuestions = questionRepository.findBySessionIdAndDifficultyAndIsDelete(
                    testSession.getId(), Difficulty.HARD, 0);

            assertThat(hardQuestions).hasSize(2);
        }

        @Test
        @DisplayName("统计会话题目数量")
        void countActiveBySessionId_shouldReturnCount() {
            long count = questionRepository.countActiveBySessionId(testSession.getId());

            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("按知识点查询")
    class FindByConceptTests {

        @BeforeEach
        void setUpQuestionsWithConcepts() {
            QuizQuestion q2 = createTestQuestion(testSession, 2, QuestionType.TRUE_FALSE, Difficulty.EASY);
            q2.setRelatedConcept("Java基础");
            entityManager.persist(q2);

            QuizQuestion q3 = createTestQuestion(testSession, 3, QuestionType.FILL_IN_BLANK, Difficulty.MEDIUM);
            q3.setRelatedConcept("Java基础");
            entityManager.persist(q3);

            QuizQuestion q4 = createTestQuestion(testSession, 4, QuestionType.EXPLANATION, Difficulty.HARD);
            q4.setRelatedConcept("面向对象");
            entityManager.persist(q4);

            entityManager.flush();
        }

        @Test
        @DisplayName("查询特定知识点的题目")
        void findActiveByRelatedConcept_shouldReturnQuestions() {
            List<QuizQuestion> javaQuestions = questionRepository.findActiveByRelatedConcept("Java基础");

            assertThat(javaQuestions).hasSize(2);
        }

        @Test
        @DisplayName("模糊查询知识点")
        void findByRelatedConceptContaining_shouldReturnMatches() {
            List<QuizQuestion> questions = questionRepository.findByRelatedConceptContaining("Java");

            assertThat(questions).hasSize(2);
        }

        @Test
        @DisplayName("查询会话中指定知识点的题目")
        void findBySessionIdAndRelatedConcept_shouldReturnQuestions() {
            List<QuizQuestion> questions = questionRepository.findBySessionIdAndRelatedConcept(
                    testSession.getId(), "面向对象");

            assertThat(questions).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Entity 便捷方法测试")
    class EntityMethodTests {

        @Test
        @DisplayName("requiresOptions 应正确判断")
        void requiresOptions_shouldReturnCorrectly() {
            savedQuestion.setQuestionType(QuestionType.SINGLE_CHOICE);
            assertThat(savedQuestion.requiresOptions()).isTrue();

            savedQuestion.setQuestionType(QuestionType.FILL_IN_BLANK);
            assertThat(savedQuestion.requiresOptions()).isFalse();

            savedQuestion.setQuestionType(QuestionType.ORDERING);
            assertThat(savedQuestion.requiresOptions()).isTrue();
        }

        @Test
        @DisplayName("isObjective 应正确判断")
        void isObjective_shouldReturnCorrectly() {
            savedQuestion.setQuestionType(QuestionType.SINGLE_CHOICE);
            assertThat(savedQuestion.isObjective()).isTrue();

            savedQuestion.setQuestionType(QuestionType.EXPLANATION);
            assertThat(savedQuestion.isObjective()).isFalse();
        }

        @Test
        @DisplayName("isSubjective 应正确判断")
        void isSubjective_shouldReturnCorrectly() {
            savedQuestion.setQuestionType(QuestionType.SHORT_ANSWER);
            assertThat(savedQuestion.isSubjective()).isTrue();

            savedQuestion.setQuestionType(QuestionType.TRUE_FALSE);
            assertThat(savedQuestion.isSubjective()).isFalse();
        }

        @Test
        @DisplayName("supportsPartialScore 应正确判断")
        void supportsPartialScore_shouldReturnCorrectly() {
            savedQuestion.setQuestionType(QuestionType.MULTIPLE_SELECT);
            assertThat(savedQuestion.supportsPartialScore()).isTrue();

            savedQuestion.setQuestionType(QuestionType.SINGLE_CHOICE);
            assertThat(savedQuestion.supportsPartialScore()).isFalse();
        }
    }
}
