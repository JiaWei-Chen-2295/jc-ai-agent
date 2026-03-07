package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.*;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("QuestionResponse Repository 测试")
class QuestionResponseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private QuestionResponseRepository responseRepository;
    @Autowired
    private QuizSessionRepository sessionRepository;
    @Autowired
    private QuizQuestionRepository questionRepository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;
    private QuizSession testSession;
    private QuizQuestion testQuestion;
    private QuestionResponse savedResponse;

    @BeforeEach
    void setUp() {
        responseRepository.deleteAll();
        questionRepository.deleteAll();
        sessionRepository.deleteAll();

        testSession = new QuizSession();
        testSession.setTenantId(TENANT_ID);
        testSession.setUserId(USER_ID);
        testSession.setQuizMode(QuizMode.ADAPTIVE);
        testSession.setStatus(QuizStatus.IN_PROGRESS);
        testSession.setIsDelete(0);
        entityManager.persistAndFlush(testSession);

        testQuestion = new QuizQuestion();
        testQuestion.setSession(testSession);
        testQuestion.setTenantId(TENANT_ID);
        testQuestion.setQuestionNo(1);
        testQuestion.setQuestionText("测试题目");
        testQuestion.setQuestionType(QuestionType.SINGLE_CHOICE);
        testQuestion.setCorrectAnswer("A");
        testQuestion.setRelatedConcept("Java基础");
        testQuestion.setDifficulty(Difficulty.EASY);
        testQuestion.setIsDelete(0);
        entityManager.persistAndFlush(testQuestion);

        savedResponse = createResponse(true, 80, 3000);
        entityManager.persistAndFlush(savedResponse);
    }

    private QuestionResponse createResponse(boolean correct, int score, int responseTimeMs) {
        QuestionResponse response = new QuestionResponse();
        response.setSession(testSession);
        response.setQuestion(testQuestion);
        response.setTenantId(TENANT_ID);
        response.setUserId(USER_ID);
        response.setUserAnswer("A");
        response.setIsCorrect(correct);
        response.setScore(score);
        response.setResponseTimeMs(responseTimeMs);
        response.setHesitationDetected(false);
        response.setConfusionDetected(false);
        response.setConceptMastery(correct ? ConceptMastery.MASTERED : ConceptMastery.UNMASTERED);
        response.setIsDelete(0);
        return response;
    }

    @Test
    @DisplayName("保存回答应生成UUID")
    void save_shouldGenerateUUID() {
        assertThat(savedResponse.getId()).isNotNull();
    }

    @Test
    @DisplayName("查询会话的所有回答")
    void findActiveBySessionId_shouldReturnResponses() {
        List<QuestionResponse> responses = responseRepository.findActiveBySessionId(testSession.getId());
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("统计正确回答数")
    void countCorrectBySessionId_shouldReturnCorrectCount() {
        entityManager.persist(createResponse(false, 0, 5000));
        entityManager.flush();

        long correctCount = responseRepository.countCorrectBySessionId(testSession.getId());
        long totalCount = responseRepository.countActiveBySessionId(testSession.getId());

        assertThat(correctCount).isEqualTo(1);
        assertThat(totalCount).isEqualTo(2);
    }

    @Test
    @DisplayName("计算平均响应时间")
    void findAvgResponseTimeBySessionId_shouldCalculate() {
        entityManager.persist(createResponse(true, 100, 5000));
        entityManager.flush();

        Double avgTime = responseRepository.findAvgResponseTimeBySessionId(testSession.getId());
        assertThat(avgTime).isEqualTo(4000.0);
    }

    @Test
    @DisplayName("Entity便捷方法-needsRemediation")
    void needsRemediation_shouldReturnCorrectly() {
        savedResponse.setConceptMastery(ConceptMastery.UNMASTERED);
        assertThat(savedResponse.needsRemediation()).isTrue();

        savedResponse.setConceptMastery(ConceptMastery.MASTERED);
        assertThat(savedResponse.needsRemediation()).isFalse();
    }

    @Test
    @DisplayName("Entity便捷方法-getCognitiveLoadSignal")
    void getCognitiveLoadSignal_shouldCalculate() {
        savedResponse.setHesitationDetected(true);
        savedResponse.setConfusionDetected(true);
        savedResponse.setIsCorrect(false);
        assertThat(savedResponse.getCognitiveLoadSignal()).isEqualTo(90);
    }

    @Test
    @DisplayName("Entity便捷方法-isSlowResponse")
    void isSlowResponse_shouldDetectSlow() {
        savedResponse.setResponseTimeMs(6000);
        assertThat(savedResponse.isSlowResponse(3000)).isTrue();

        savedResponse.setResponseTimeMs(4000);
        assertThat(savedResponse.isSlowResponse(3000)).isFalse();
    }
}
