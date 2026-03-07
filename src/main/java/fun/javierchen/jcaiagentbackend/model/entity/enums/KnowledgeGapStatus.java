package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 知识缺口状态枚举
 *
 * @author JavierChen
 */
public enum KnowledgeGapStatus {
    /**
     * 活跃 - 尚未解决
     */
    ACTIVE("active", "活跃"),

    /**
     * 已解决
     */
    RESOLVED("resolved", "已解决");

    private final String code;
    private final String description;

    KnowledgeGapStatus(String code, String description) {
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
     * @return KnowledgeGapStatus 或 null
     */
    public static KnowledgeGapStatus fromCode(String code) {
        for (KnowledgeGapStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
