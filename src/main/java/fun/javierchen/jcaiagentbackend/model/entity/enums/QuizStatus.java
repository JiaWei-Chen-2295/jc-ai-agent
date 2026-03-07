package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 测验会话状态枚举
 * 定义测验会话的生命周期状态
 *
 * @author JavierChen
 */
public enum QuizStatus {
    /**
     * 进行中
     */
    IN_PROGRESS("in_progress", "进行中"),

    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),

    /**
     * 已暂停
     */
    PAUSED("paused", "已暂停"),

    /**
     * 已超时
     */
    TIMEOUT("timeout", "已超时"),

    /**
     * 已放弃
     */
    ABANDONED("abandoned", "已放弃");

    private final String code;
    private final String description;

    QuizStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取状态
     *
     * @param code 编码
     * @return QuizStatus 或 null
     */
    public static QuizStatus fromCode(String code) {
        for (QuizStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为终态（不可恢复）
     *
     * @return true 表示终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == TIMEOUT || this == ABANDONED;
    }

    /**
     * 判断是否可暂停
     *
     * @return true 表示可暂停
     */
    public boolean canPause() {
        return this == IN_PROGRESS;
    }

    /**
     * 判断是否可恢复
     *
     * @return true 表示可恢复
     */
    public boolean canResume() {
        return this == PAUSED;
    }
}
