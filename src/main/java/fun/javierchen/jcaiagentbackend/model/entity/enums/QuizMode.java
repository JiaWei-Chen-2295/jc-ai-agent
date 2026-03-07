package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 测验模式枚举
 * 定义测验的难度模式
 *
 * @author JavierChen
 */
public enum QuizMode {
    /**
     * 简单模式
     */
    EASY("easy", "简单模式"),

    /**
     * 中等模式
     */
    MEDIUM("medium", "中等模式"),

    /**
     * 困难模式
     */
    HARD("hard", "困难模式"),

    /**
     * 自适应模式 - 根据用户表现动态调整难度
     */
    ADAPTIVE("adaptive", "自适应模式");

    private final String code;
    private final String description;

    QuizMode(String code, String description) {
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
     * 根据编码获取模式
     *
     * @param code 编码
     * @return QuizMode 或 null
     */
    public static QuizMode fromCode(String code) {
        for (QuizMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return null;
    }
}
