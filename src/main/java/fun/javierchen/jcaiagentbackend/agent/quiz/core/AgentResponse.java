package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 响应
 * Agent 执行完成后的最终响应
 *
 * @author JavierChen
 */
@Data
@Builder
public class AgentResponse {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 响应类型
     */
    private ResponseType type;

    /**
     * 生成的题目列表
     */
    private List<GeneratedQuestion> questions;

    /**
     * 答案评估结果
     */
    private AnswerEvaluation evaluation;

    /**
     * 给用户的反馈信息
     */
    private String feedback;

    /**
     * 下一步行动建议
     */
    private String nextAction;

    /**
     * 是否应该结束测验
     */
    private boolean shouldFinish;

    /**
     * 是否启用了降级生成
     */
    private boolean fallbackApplied;

    /**
     * 降级原因
     */
    private String fallbackReason;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 响应类型枚举
     */
    public enum ResponseType {
        /**
         * 题目生成
         */
        QUESTION_GENERATED,

        /**
         * 答案已评估
         */
        ANSWER_EVALUATED,

        /**
         * 测验完成
         */
        QUIZ_COMPLETED,

        /**
         * 需要补漏
         */
        REMEDIATION_NEEDED,

        /**
         * 错误
         */
        ERROR
    }

    /**
     * 生成的题目
     */
    @Data
    @Builder
    public static class GeneratedQuestion {
        private String type;
        private String text;
        private List<String> options;
        private String correctAnswer;
        private String relatedConcept;
        private String difficulty;
        private String explanation;
    }

    /**
     * 答案评估结果
     */
    @Data
    @Builder
    public static class AnswerEvaluation {
        private boolean correct;
        private int score;
        private int depthScore;
        private int loadScore;
        private boolean hesitationDetected;
        private boolean confusionDetected;
        private String feedback;
        private String conceptMastery;
    }

    /**
     * 创建成功响应
     */
    public static AgentResponse success(ResponseType type) {
        return AgentResponse.builder()
                .success(true)
                .type(type)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static AgentResponse error(String errorMessage) {
        return AgentResponse.builder()
                .success(false)
                .type(ResponseType.ERROR)
                .errorMessage(errorMessage)
                .build();
    }
}
