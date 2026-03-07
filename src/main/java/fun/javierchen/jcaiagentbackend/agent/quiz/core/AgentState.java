package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 状态
 * 跟踪 Agent ReAct 循环的执行状态
 *
 * @author JavierChen
 */
@Data
public class AgentState {

    /**
     * 当前迭代次数
     */
    private int iteration = 0;

    /**
     * 是否已达成目标
     */
    private boolean goalAchieved = false;

    /**
     * 是否陷入死循环
     */
    private boolean stuck = false;

    /**
     * 历史行动记录 (用于检测重复)
     */
    private List<String> actionHistory = new ArrayList<>();

    /**
     * 最后一次决策
     */
    private AgentDecision lastDecision;

    /**
     * 最后一次工具执行结果
     */
    private ToolResult lastToolResult;

    /**
     * 状态快照 (用于持久化)
     */
    private Map<String, Object> snapshot = new HashMap<>();

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 应用工具执行结果
     */
    public void applyResult(ToolResult result) {
        this.lastToolResult = result;
        if (result != null) {
            this.goalAchieved = result.isTerminal();
            if (result.getActionName() != null) {
                this.actionHistory.add(result.getActionName());
            }
        }
    }

    /**
     * 记录决策
     */
    public void recordDecision(AgentDecision decision) {
        this.lastDecision = decision;
        this.iteration++;
    }

    /**
     * 检测是否重复执行相同动作
     */
    public boolean hasRepeatedAction(String actionName, int threshold) {
        if (actionHistory.size() < threshold) {
            return false;
        }
        int count = 0;
        for (int i = actionHistory.size() - 1; i >= Math.max(0, actionHistory.size() - threshold); i--) {
            if (actionName.equals(actionHistory.get(i))) {
                count++;
            }
        }
        return count >= threshold;
    }

    /**
     * 标记为死循环
     */
    public void markAsStuck(String reason) {
        this.stuck = true;
        this.errorMessage = reason;
    }

    /**
     * 保存状态快照
     */
    public void saveSnapshot(String key, Object value) {
        this.snapshot.put(key, value);
    }

    /**
     * 获取状态快照
     */
    @SuppressWarnings("unchecked")
    public <T> T getSnapshot(String key, Class<T> clazz) {
        Object value = snapshot.get(key);
        return value != null ? (T) value : null;
    }
}
