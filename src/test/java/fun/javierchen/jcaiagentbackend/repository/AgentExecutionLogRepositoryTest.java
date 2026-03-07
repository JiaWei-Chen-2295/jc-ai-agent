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
@DisplayName("AgentExecutionLog Repository 测试")
class AgentExecutionLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private AgentExecutionLogRepository logRepository;
    @Autowired
    private QuizSessionRepository sessionRepository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;
    private QuizSession testSession;
    private AgentExecutionLog savedLog;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        sessionRepository.deleteAll();

        testSession = new QuizSession();
        testSession.setTenantId(TENANT_ID);
        testSession.setUserId(USER_ID);
        testSession.setQuizMode(QuizMode.ADAPTIVE);
        testSession.setStatus(QuizStatus.IN_PROGRESS);
        testSession.setIsDelete(0);
        entityManager.persistAndFlush(testSession);

        savedLog = createLog(1, AgentPhase.THOUGHT, null, 100);
        entityManager.persistAndFlush(savedLog);
    }

    private AgentExecutionLog createLog(int iteration, AgentPhase phase, String toolName, int execTime) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setSession(testSession);
        log.setTenantId(TENANT_ID);
        log.setIteration(iteration);
        log.setPhase(phase);
        log.setToolName(toolName);
        log.setExecutionTimeMs(execTime);
        log.setInputData(Map.of("key", "value"));
        log.setOutputData(Map.of("result", "success"));
        log.setIsDelete(0);
        return log;
    }

    @Test
    @DisplayName("保存日志应生成UUID")
    void save_shouldGenerateUUID() {
        assertThat(savedLog.getId()).isNotNull();
    }

    @Test
    @DisplayName("查询会话的所有日志")
    void findActiveBySessionId_shouldReturnLogs() {
        entityManager.persist(createLog(1, AgentPhase.ACTION, "QuizGenerator", 500));
        entityManager.persist(createLog(1, AgentPhase.OBSERVATION, null, 200));
        entityManager.flush();

        List<AgentExecutionLog> logs = logRepository.findActiveBySessionId(testSession.getId());
        assertThat(logs).hasSize(3);
    }

    @Test
    @DisplayName("查询THOUGHT阶段日志")
    void findThoughtsBySessionId_shouldReturnThoughtsOnly() {
        entityManager.persist(createLog(1, AgentPhase.ACTION, "Tool", 100));
        entityManager.flush();

        List<AgentExecutionLog> thoughts = logRepository.findThoughtsBySessionId(testSession.getId());
        assertThat(thoughts).hasSize(1);
        assertThat(thoughts.get(0).getPhase()).isEqualTo(AgentPhase.THOUGHT);
    }

    @Test
    @DisplayName("获取最大迭代次数")
    void findMaxIterationBySessionId_shouldReturnMax() {
        entityManager.persist(createLog(2, AgentPhase.THOUGHT, null, 100));
        entityManager.persist(createLog(3, AgentPhase.THOUGHT, null, 100));
        entityManager.flush();

        Integer maxIteration = logRepository.findMaxIterationBySessionId(testSession.getId());
        assertThat(maxIteration).isEqualTo(3);
    }

    @Test
    @DisplayName("计算平均执行时间")
    void findAvgExecutionTimeBySessionId_shouldCalculate() {
        entityManager.persist(createLog(2, AgentPhase.ACTION, "Tool", 200));
        entityManager.persist(createLog(3, AgentPhase.OBSERVATION, null, 300));
        entityManager.flush();

        Double avgTime = logRepository.findAvgExecutionTimeBySessionId(testSession.getId());
        assertThat(avgTime).isEqualTo(200.0);
    }

    @Test
    @DisplayName("查询超时日志")
    void findTimeoutLogsBySessionId_shouldReturnTimeouts() {
        entityManager.persist(createLog(2, AgentPhase.ACTION, "SlowTool", 6000));
        entityManager.flush();

        List<AgentExecutionLog> timeouts = logRepository.findTimeoutLogsBySessionId(testSession.getId());
        assertThat(timeouts).hasSize(1);
        assertThat(timeouts.get(0).getExecutionTimeMs()).isGreaterThan(5000);
    }

    @Test
    @DisplayName("静态工厂方法-thought")
    void thought_shouldCreateCorrectLog() {
        Map<String, Object> input = Map.of("state", "analyzing");
        Map<String, Object> output = Map.of("decision", "continue");

        AgentExecutionLog log = AgentExecutionLog.thought(testSession, 1, input, output);

        assertThat(log.getPhase()).isEqualTo(AgentPhase.THOUGHT);
        assertThat(log.getIteration()).isEqualTo(1);
        assertThat(log.getInputData()).isEqualTo(input);
    }

    @Test
    @DisplayName("静态工厂方法-action")
    void action_shouldCreateCorrectLog() {
        Map<String, Object> input = Map.of("topic", "Java");

        AgentExecutionLog log = AgentExecutionLog.action(testSession, 2, "QuizGenerator", input);

        assertThat(log.getPhase()).isEqualTo(AgentPhase.ACTION);
        assertThat(log.getToolName()).isEqualTo("QuizGenerator");
    }

    @Test
    @DisplayName("Entity便捷方法-isTimeout")
    void isTimeout_shouldDetectTimeout() {
        savedLog.setExecutionTimeMs(6000);
        assertThat(savedLog.isTimeout()).isTrue();

        savedLog.setExecutionTimeMs(3000);
        assertThat(savedLog.isTimeout()).isFalse();
    }
}
