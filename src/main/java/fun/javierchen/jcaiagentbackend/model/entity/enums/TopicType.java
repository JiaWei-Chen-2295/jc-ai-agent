package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 主题类型枚举
 * 定义知识状态的主题类型
 *
 * @author JavierChen
 */
public enum TopicType {
    /**
     * 文档级别
     */
    DOCUMENT("document", "文档"),

    /**
     * 概念级别
     */
    CONCEPT("concept", "概念");

    private final String code;
    private final String description;

    TopicType(String code, String description) {
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
     * 根据编码获取主题类型
     *
     * @param code 编码
     * @return TopicType 或 null
     */
    public static TopicType fromCode(String code) {
        for (TopicType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
