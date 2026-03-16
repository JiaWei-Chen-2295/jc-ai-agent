package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.agent.quiz.QuizReActAgent;
import fun.javierchen.jcaiagentbackend.agent.quiz.analyzer.CognitiveAnalyzer;
import fun.javierchen.jcaiagentbackend.agent.quiz.cache.KnowledgeStateFlushService;
import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentResponse;
import fun.javierchen.jcaiagentbackend.agent.quiz.decision.DecisionEngine;
import fun.javierchen.jcaiagentbackend.agent.quiz.inventory.ConceptInventoryService;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.KnowledgeCoverageVO;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.SubmitAnswerRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.SubmitAnswerResponse;
import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizMode;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuestionResponse;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizQuestion;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UnmasteredKnowledge;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.repository.QuestionResponseRepository;
import fun.javierchen.jcaiagentbackend.repository.QuizQuestionRepository;
import fun.javierchen.jcaiagentbackend.repository.QuizSessionRepository;
import fun.javierchen.jcaiagentbackend.repository.UnmasteredKnowledgeRepository;
import fun.javierchen.jcaiagentbackend.repository.UserKnowledgeStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizSessionServiceImpl 测试")
class QuizSessionServiceImplTest {

    @Mock
    private QuizSessionRepository sessionRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuestionResponseRepository responseRepository;

    @Mock
    private UserKnowledgeStateRepository knowledgeStateRepository;

    @Mock
    private UnmasteredKnowledgeRepository unmasteredKnowledgeRepository;

    @Mock
    private QuizReActAgent quizAgent;

    @Mock
    private CognitiveAnalyzer cognitiveAnalyzer;

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private ConceptInventoryService conceptInventoryService;

    @Mock
    private QuizRedisService quizRedisService;

    @Mock
    private KnowledgeStateFlushService knowledgeStateFlushService;

    @InjectMocks
    private QuizSessionServiceImpl service;

    @Test
    @DisplayName("提交答案时应把 Redis 缓冲知识状态并入结束判定")
    void submitAnswer_shouldMergeBufferedKnowledgeStatesBeforeDecision() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        QuizSession session = buildSession(sessionId, 1L, 100L);
        QuizQuestion question = buildQuestion(questionId, session, "Redis缓存");
        UserKnowledgeState dbState = buildState("Java基础", 82, 28, 84, 6, 5);

        when(sessionRepository.findActiveById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveById(questionId)).thenReturn(Optional.of(question));
        when(knowledgeStateRepository.findActiveByUserId(100L)).thenReturn(List.of(dbState));
        when(responseRepository.findAvgResponseTimeByUserId(100L)).thenReturn(12000.0);
        when(responseRepository.save(any(QuestionResponse.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(QuizSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizAgent.execute(any())).thenReturn(AgentResponse.builder()
                .success(true)
                .type(AgentResponse.ResponseType.ANSWER_EVALUATED)
                .evaluation(AgentResponse.AnswerEvaluation.builder()
                        .correct(true)
                        .score(100)
                        .feedback("很好")
                        .conceptMastery(ConceptMastery.MASTERED.name())
                        .build())
                .build());
        when(cognitiveAnalyzer.updateKnowledgeState(eq(1L), eq(100L), eq("Redis缓存"), any(QuestionResponse.class), eq(sessionId.toString())))
                .thenReturn(buildState("Redis缓存", 75, 32, 78, 1, 1));
        when(quizRedisService.getAllKnowledgeStates(sessionId.toString())).thenReturn(Map.of(
                "Redis缓存", Map.of(
                        "depth", "75",
                        "load", "32",
                        "stability", "78",
                        "total", "1",
                        "correct", "1")));
        when(decisionEngine.checkShouldFinish(any(), eq(session))).thenReturn(true);

        SubmitAnswerRequest request = new SubmitAnswerRequest();
        request.setQuestionId(questionId);
        request.setAnswer("使用 TTL 和淘汰策略");
        request.setResponseTimeMs(8000);

        SubmitAnswerResponse response = service.submitAnswer(sessionId, 1L, 100L, request);

        ArgumentCaptor<List<UserKnowledgeState>> statesCaptor = ArgumentCaptor.forClass(List.class);
        verify(decisionEngine).checkShouldFinish(statesCaptor.capture(), eq(session));
        verify(knowledgeStateFlushService).flush(sessionId.toString(), 1L, 100L);

        List<UserKnowledgeState> effectiveStates = statesCaptor.getValue();
        assertThat(effectiveStates).extracting(UserKnowledgeState::getTopicName)
                .containsExactlyInAnyOrder("Java基础", "Redis缓存");
        assertThat(effectiveStates)
                .filteredOn(s -> "Redis缓存".equals(s.getTopicName()))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.getUnderstandingDepth()).isEqualTo(75);
                    assertThat(state.getCognitiveLoadScore()).isEqualTo(32);
                    assertThat(state.getStabilityScore()).isEqualTo(78);
                    assertThat(state.getTotalQuestions()).isEqualTo(1);
                    assertThat(state.getCorrectAnswers()).isEqualTo(1);
                });
        assertThat(response.getQuizCompleted()).isTrue();
    }

    @Test
    @DisplayName("知识覆盖率应合并 DB、Redis 和未测概念")
    void getKnowledgeCoverage_shouldMergeBufferedAndUntestedConcepts() {
        UUID sessionId = UUID.randomUUID();
        QuizSession session = buildSession(sessionId, 1L, 100L);
        session.setAgentState(Map.of("concepts", List.of("Java基础", "Redis缓存", "线程安全")));

        UserKnowledgeState masteredState = buildState("Java基础", 85, 30, 88, 6, 5);

        when(sessionRepository.findActiveById(sessionId)).thenReturn(Optional.of(session));
        when(knowledgeStateRepository.findActiveByUserId(100L)).thenReturn(List.of(masteredState));
        when(quizRedisService.getConcepts(sessionId.toString())).thenReturn(Set.of("Java基础", "Redis缓存", "线程安全"));
        when(quizRedisService.getAllKnowledgeStates(sessionId.toString())).thenReturn(Map.of(
                "Redis缓存", Map.of(
                        "depth", "65",
                        "load", "45",
                        "stability", "60",
                        "total", "2",
                        "correct", "1")));
        when(responseRepository.countActiveBySessionId(sessionId)).thenReturn(5L);

        KnowledgeCoverageVO coverage = service.getKnowledgeCoverage(sessionId, 100L);

        assertThat(coverage.getConceptSource()).isEqualTo("REDIS");
        assertThat(coverage.getTotalConcepts()).isEqualTo(3);
        assertThat(coverage.getTestedConcepts()).isEqualTo(2);
        assertThat(coverage.getMasteredConcepts()).isEqualTo(1);
        assertThat(coverage.getCoveragePercent()).isEqualTo(66.7);
        assertThat(coverage.getAnsweredQuestions()).isEqualTo(5);
        assertThat(coverage.getConcepts())
                .extracting(KnowledgeCoverageVO.ConceptDetail::getName, KnowledgeCoverageVO.ConceptDetail::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Java基础", "MASTERED"),
                        org.assertj.core.groups.Tuple.tuple("Redis缓存", "TESTING"),
                        org.assertj.core.groups.Tuple.tuple("线程安全", "UNTESTED"));
    }

    private QuizSession buildSession(UUID sessionId, Long tenantId, Long userId) {
        QuizSession session = new QuizSession();
        session.setId(sessionId);
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setQuizMode(QuizMode.ADAPTIVE);
        session.setStatus(QuizStatus.IN_PROGRESS);
        session.setCurrentQuestionNo(8);
        session.setTotalQuestions(10);
        session.setScore(0);
        session.setDocumentScope(List.of(1L, 2L));
        return session;
    }

    private QuizQuestion buildQuestion(UUID questionId, QuizSession session, String concept) {
        QuizQuestion question = new QuizQuestion();
        question.setId(questionId);
        question.setTenantId(session.getTenantId());
        question.setSession(session);
        question.setQuestionNo(session.getCurrentQuestionNo());
        question.setQuestionText("Redis 的 TTL 有什么作用？");
        question.setQuestionType(QuestionType.SHORT_ANSWER);
        question.setCorrectAnswer("控制缓存生命周期");
        question.setExplanation("TTL 可以避免脏数据长期存在。");
        question.setRelatedConcept(concept);
        question.setDifficulty(Difficulty.MEDIUM);
        return question;
    }

    private UserKnowledgeState buildState(String topicName, int depth, int load, int stability, int total, int correct) {
        UserKnowledgeState state = new UserKnowledgeState();
        state.setTenantId(1L);
        state.setUserId(100L);
        state.setTopicType(TopicType.CONCEPT);
        state.setTopicId(topicName);
        state.setTopicName(topicName);
        state.setUnderstandingDepth(depth);
        state.setCognitiveLoadScore(load);
        state.setStabilityScore(stability);
        state.setTotalQuestions(total);
        state.setCorrectAnswers(correct);
        return state;
    }
}
