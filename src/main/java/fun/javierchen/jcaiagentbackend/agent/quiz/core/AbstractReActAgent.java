package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import fun.javierchen.jcaiagentbackend.model.entity.enums.AgentPhase;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.AgentExecutionLog;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import fun.javierchen.jcaiagentbackend.repository.AgentExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 抽象 ReAct Agent
 * 实现通用的 ReAct 循环逻辑: Thought → Action → Observation
 *
 * @author JavierChen
 */
@Slf4j
public abstract class AbstractReActAgent implements BaseAgent {

    /**
     * 最大迭代次数
     */
    protected final int maxIterations;

    /**
     * 检测重复动作的阈值
     */
    protected final int repeatThreshold;

    /**
     * Agent 执行日志 Repository
     */
    protected final AgentExecutionLogRepository logRepository;

    protected AbstractReActAgent(AgentExecutionLogRepository logRepository) {
        this(logRepository, 10, 3);
    }

    protected AbstractReActAgent(AgentExecutionLogRepository logRepository,
            int maxIterations, int repeatThreshold) {
        this.logRepository = logRepository;
        this.maxIterations = maxIterations;
        this.repeatThreshold = repeatThreshold;
    }

    @Override
    public final AgentResponse execute(AgentContext context) {
        log.info("Agent [{}] 开始执行, 会话ID: {}", getName(), context.getSessionId());

        // 加载或初始化状态
        AgentState state = loadState(context);

        try {
            // ReAct 循环
            for (int i = 0; i < maxIterations; i++) {
                state.setIteration(i + 1);
                log.debug("迭代 {}/{}", i + 1, maxIterations);

                // Thought: 分析当前状态，做出决策
                AgentDecision decision = think(context, state);
                state.recordDecision(decision);
                logExecution(context.getSession(), i + 1, AgentPhase.THOUGHT, decision);

                // 检查是否完成
                if (decision.getType() == AgentDecision.DecisionType.FINISH) {
                    log.info("Agent 决定完成任务: {}", decision.getReasoning());
                    return buildResponse(state);
                }

                // 检查是否需要降级
                if (decision.getType() == AgentDecision.DecisionType.FALLBACK) {
                    log.warn("Agent 触发降级: {}", decision.getReasoning());
                    return handleFallback(context, state);
                }

                // Action: 执行工具调用
                ToolResult result = act(decision, context, state);
                logExecution(context.getSession(), i + 1, AgentPhase.ACTION, result);

                // Observation: 处理执行结果
                state.applyResult(result);
                logExecution(context.getSession(), i + 1, AgentPhase.OBSERVATION, state);

                // 检查是否达成目标
                if (state.isGoalAchieved()) {
                    log.info("目标已达成，结束循环");
                    break;
                }

                // 检查是否陷入死循环
                if (isStuck(state, decision)) {
                    log.warn("检测到死循环，触发降级处理");
                    state.markAsStuck("检测到重复动作或无进展");
                    return handleDeadlock(context, state);
                }
            }

            // 达到最大迭代次数
            if (!state.isGoalAchieved()) {
                log.warn("达到最大迭代次数 {}，强制结束", maxIterations);
                state.markAsStuck("达到最大迭代次数");
            }

            return buildResponse(state);
        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            state.setErrorMessage(e.getMessage());
            return AgentResponse.error("Agent 执行失败: " + e.getMessage());
        } finally {
            // 保存状态
            saveState(context, state);
        }
    }

    /**
     * 思考阶段: 分析当前状态，做出决策
     * 子类必须实现
     */
    protected abstract AgentDecision think(AgentContext context, AgentState state);

    /**
     * 行动阶段: 执行工具调用
     * 子类必须实现
     */
    protected abstract ToolResult act(AgentDecision decision, AgentContext context, AgentState state);

    /**
     * 构建最终响应
     * 子类必须实现
     */
    protected abstract AgentResponse buildResponse(AgentState state);

    /**
     * 加载 Agent 状态
     * 子类可重写以实现状态持久化
     */
    protected AgentState loadState(AgentContext context) {
        // 默认创建新状态
        return new AgentState();
    }

    /**
     * 保存 Agent 状态
     * 子类可重写以实现状态持久化
     */
    protected void saveState(AgentContext context, AgentState state) {
        // 默认不持久化
    }

    /**
     * 检测是否陷入死循环
     */
    protected boolean isStuck(AgentState state, AgentDecision decision) {
        if (decision.getToolName() == null) {
            return false;
        }
        return state.hasRepeatedAction(decision.getToolName(), repeatThreshold);
    }

    /**
     * 处理死循环情况
     */
    protected AgentResponse handleDeadlock(AgentContext context, AgentState state) {
        log.warn("处理死循环: {}", state.getErrorMessage());
        return AgentResponse.builder()
                .success(false)
                .type(AgentResponse.ResponseType.ERROR)
                .errorMessage("Agent 陷入死循环，已终止执行")
                .build();
    }

    /**
     * 处理降级情况
     */
    protected AgentResponse handleFallback(AgentContext context, AgentState state) {
        log.warn("处理降级");
        return AgentResponse.builder()
                .success(true)
                .type(AgentResponse.ResponseType.REMEDIATION_NEEDED)
                .feedback("系统切换到基础模式")
                .build();
    }

    /**
     * 记录执行日志
     */
    protected void logExecution(QuizSession session, int iteration, AgentPhase phase, Object data) {
        if (session == null || logRepository == null) {
            return;
        }

        try {
            AgentExecutionLog logEntry = new AgentExecutionLog();
            logEntry.setTenantId(session.getTenantId());
            logEntry.setSession(session);
            logEntry.setIteration(iteration);
            logEntry.setPhase(phase);
            logEntry.setTimestamp(LocalDateTime.now());

            Map<String, Object> inputData = new HashMap<>();
            Map<String, Object> outputData = new HashMap<>();

            if (data instanceof AgentDecision decision) {
                inputData.put("type", decision.getType());
                inputData.put("toolName", decision.getToolName());
                outputData.put("reasoning", decision.getReasoning());
            } else if (data instanceof ToolResult result) {
                inputData.put("actionName", result.getActionName());
                inputData.put("success", result.isSuccess());
                outputData.put("terminal", result.isTerminal());
                if (result.getErrorMessage() != null) {
                    outputData.put("error", result.getErrorMessage());
                }
            } else if (data instanceof AgentState state) {
                inputData.put("iteration", state.getIteration());
                inputData.put("goalAchieved", state.isGoalAchieved());
                outputData.put("stuck", state.isStuck());
            }

            logEntry.setInputData(inputData);
            logEntry.setOutputData(outputData);
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("保存执行日志失败", e);
        }
    }
}
