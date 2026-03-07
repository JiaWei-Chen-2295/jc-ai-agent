package fun.javierchen.jcaiagentbackend.agent.quiz;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.*;
import fun.javierchen.jcaiagentbackend.agent.quiz.decision.DecisionEngine;
import fun.javierchen.jcaiagentbackend.agent.quiz.tools.*;
import fun.javierchen.jcaiagentbackend.repository.AgentExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测验 ReAct Agent
 * 负责测验的智能决策和执行
 *
 * @author JavierChen
 */
@Slf4j
@Component
public class QuizReActAgent extends AbstractReActAgent {

    private final DecisionEngine decisionEngine;
    private final Map<String, AgentTool> toolMap;

    public QuizReActAgent(AgentExecutionLogRepository logRepository,
            DecisionEngine decisionEngine,
            QuizGeneratorTool quizGeneratorTool,
            KnowledgeRetrieverTool knowledgeRetrieverTool,
            UserAnalyzerTool userAnalyzerTool,
            AnswerEvaluatorTool answerEvaluatorTool) {
        super(logRepository);
        this.decisionEngine = decisionEngine;

        // 注册工具
        this.toolMap = new HashMap<>();
        registerTool(quizGeneratorTool);
        registerTool(knowledgeRetrieverTool);
        registerTool(userAnalyzerTool);
        registerTool(answerEvaluatorTool);

        log.info("QuizReActAgent 初始化完成，已注册 {} 个工具", toolMap.size());
    }

    private void registerTool(AgentTool tool) {
        toolMap.put(tool.getName(), tool);
    }


    @Override
    public String getName() {
        return "QuizReActAgent";
    }

    @Override
    public String getDescription() {
        return "智能测验 Agent，实现自适应测验和认知分析";
    }

    @Override
    protected AgentDecision think(AgentContext context, AgentState state) {
        log.debug("Thought 阶段: 分析上下文并做出决策");
        return decisionEngine.decide(context, state);
    }

    @Override
    protected ToolResult act(AgentDecision decision, AgentContext context, AgentState state) {
        log.debug("Action 阶段: 执行 {}", decision.getToolName());

        if (decision.getType() != AgentDecision.DecisionType.CALL_TOOL) {
            return ToolResult.success(null, "NoAction");
        }

        String toolName = decision.getToolName();
        AgentTool tool = toolMap.get(toolName);

        if (tool == null) {
            log.warn("未找到工具: {}", toolName);
            return ToolResult.failure("未找到工具: " + toolName, toolName);
        }

        try {
            long startTime = System.currentTimeMillis();
            ToolResult result = tool.execute(decision.getToolParams());
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            log.debug("工具 {} 执行完成，耗时 {}ms", toolName, result.getExecutionTimeMs());
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return ToolResult.failure("工具执行失败: " + e.getMessage(), toolName);
        }
    }

    @Override
    protected AgentResponse buildResponse(AgentState state) {
        ToolResult lastResult = state.getLastToolResult();

        if (state.isStuck()) {
            return AgentResponse.builder()
                    .success(false)
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(state.getErrorMessage())
                    .build();
        }

        if (lastResult == null) {
            return AgentResponse.builder()
                    .success(true)
                    .type(AgentResponse.ResponseType.QUIZ_COMPLETED)
                    .feedback("测验完成")
                    .shouldFinish(true)
                    .build();
        }

        // 根据最后执行的工具构建响应
        String actionName = lastResult.getActionName();
        Object data = lastResult.getData();

        return switch (actionName) {
            case "QuizGenerator" -> buildQuestionResponse(data, state);
            case "AnswerEvaluator" -> buildEvaluationResponse(data);
            default -> AgentResponse.builder()
                    .success(lastResult.isSuccess())
                    .type(AgentResponse.ResponseType.QUESTION_GENERATED)
                    .build();
        };
    }

    /**
     * 构建题目生成响应
     */
    @SuppressWarnings("unchecked")
    private AgentResponse buildQuestionResponse(Object data, AgentState state) {
        log.debug("构建题目响应，data 类型: {}, data: {}", 
                data != null ? data.getClass().getName() : "null", data);
        
        if (data instanceof List<?> list) {
            log.debug("data 是 List 类型，大小: {}", list.size());
            
            List<AgentResponse.GeneratedQuestion> questions = list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> {
                        Map<String, Object> map = (Map<String, Object>) item;
                        log.debug("解析题目: text={}, type={}", map.get("text"), map.get("type"));
                        
                        // 处理 options 字段（某些题型可能没有选项）
                        List<String> options = null;
                        Object optionsObj = map.get("options");
                        if (optionsObj instanceof List) {
                            options = (List<String>) optionsObj;
                        }
                        
                        // 处理 correct_answer 字段（可能是字符串或数组）
                        String correctAnswer = null;
                        Object answerObj = map.get("correct_answer");
                        if (answerObj instanceof String) {
                            correctAnswer = (String) answerObj;
                        } else if (answerObj instanceof List) {
                            // 多选题的答案是数组，转换为逗号分隔的字符串
                            List<?> answerList = (List<?>) answerObj;
                            correctAnswer = answerList.stream()
                                    .map(Object::toString)
                                    .collect(java.util.stream.Collectors.joining(","));
                        }
                        
                        return AgentResponse.GeneratedQuestion.builder()
                                .type((String) map.get("type"))
                                .text((String) map.get("text"))
                                .options(options)
                                .correctAnswer(correctAnswer)
                                .relatedConcept((String) map.get("related_concept"))
                                .difficulty((String) map.get("difficulty"))
                                .explanation((String) map.get("explanation"))
                                .build();
                    })
                    .toList();

            log.info("成功构建 {} 道题目的响应", questions.size());
            
            return AgentResponse.builder()
                    .success(true)
                    .type(AgentResponse.ResponseType.QUESTION_GENERATED)
                    .questions(questions)
                    .fallbackApplied(Boolean.TRUE.equals(state.getLastDecision().getToolParams().get("forcedFallback")))
                    .fallbackReason((String) state.getLastDecision().getToolParams().get("fallbackReason"))
                    .build();
        }

        log.warn("data 不是 List 类型或为 null，返回空题目响应");
        return AgentResponse.builder()
                .success(true)
                .type(AgentResponse.ResponseType.QUESTION_GENERATED)
                .build();
    }

    /**
     * 构建答案评估响应
     */
    @SuppressWarnings("unchecked")
    private AgentResponse buildEvaluationResponse(Object data) {
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> evalData = (Map<String, Object>) map;

            AgentResponse.AnswerEvaluation evaluation = AgentResponse.AnswerEvaluation.builder()
                    .correct((Boolean) evalData.getOrDefault("isCorrect", false))
                    .score((Integer) evalData.getOrDefault("score", 0))
                    .depthScore((Integer) evalData.getOrDefault("depthScore", 50))
                    .loadScore((Integer) evalData.getOrDefault("loadScore", 50))
                    .hesitationDetected((Boolean) evalData.getOrDefault("hesitationDetected", false))
                    .confusionDetected((Boolean) evalData.getOrDefault("confusionDetected", false))
                    .feedback((String) evalData.get("feedback"))
                    .conceptMastery((String) evalData.get("conceptMastery"))
                    .build();

            return AgentResponse.builder()
                    .success(true)
                    .type(AgentResponse.ResponseType.ANSWER_EVALUATED)
                    .evaluation(evaluation)
                    .feedback(evaluation.getFeedback())
                    .build();
        }

        return AgentResponse.builder()
                .success(true)
                .type(AgentResponse.ResponseType.ANSWER_EVALUATED)
                .build();
    }

    @Override
    protected AgentState loadState(AgentContext context) {
        // 从会话中恢复状态
        if (context.getSession() != null && context.getSession().getAgentState() != null) {
            AgentState state = new AgentState();
            state.getSnapshot().putAll(context.getSession().getAgentState());
            return state;
        }
        return new AgentState();
    }

    @Override
    protected void saveState(AgentContext context, AgentState state) {
        // 保存状态到会话
        if (context.getSession() != null) {
            context.getSession().setAgentState(state.getSnapshot());
        }
    }
}
