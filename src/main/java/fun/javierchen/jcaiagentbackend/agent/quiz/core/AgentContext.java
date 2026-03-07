package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import fun.javierchen.jcaiagentbackend.model.entity.quiz.QuizSession;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 上下文
 * 包含 Agent 执行所需的全部上下文信息
 *
 * @author JavierChen
 */
@Data
@Builder
public class AgentContext {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 当前测验会话
     */
    private QuizSession session;

    /**
     * 用户知识状态列表
     */
    private List<UserKnowledgeState> knowledgeStates;

    /**
     * 文档范围 (要测验的文档ID列表)
     */
    private List<Long> documentScope;

    /**
     * 用户输入 (如答题答案)
     */
    private String userInput;

    /**
     * 用户响应时间 (毫秒)
     */
    private Integer responseTimeMs;

    /**
     * 额外参数
     */
    private Map<String, Object> extras;

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return session != null ? session.getId().toString() : null;
    }
}
