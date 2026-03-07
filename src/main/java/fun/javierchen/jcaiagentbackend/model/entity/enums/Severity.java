package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 严重程度枚举
 * 定义知识缺口的严重程度
 *
 * @author JavierChen
 */
public enum Severity {
    /**
     * 高严重度 - 需要立即处理
     */
    HIGH("high", "高", 3),

    /**
     * 中严重度
     */
    MEDIUM("medium", "中", 2),

    /**
     * 低严重度
     */
    LOW("low", "低", 1);

    private final String code;
    private final String description;
    private final int priority;

    Severity(String code, String description, int priority) {
        this.code = code;
        this.description = description;
        this.priority = priority;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 根据编码获取严重程度
     *
     * @param code 编码
     * @return Severity 或 null
     */
    public static Severity fromCode(String code) {
        for (Severity severity : values()) {
            if (severity.code.equalsIgnoreCase(code)) {
                return severity;
            }
        }
        return null;
    }
}
