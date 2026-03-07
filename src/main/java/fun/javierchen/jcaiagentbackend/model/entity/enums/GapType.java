package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 知识缺口类型枚举
 * 定义知识缺口的类型
 *
 * @author JavierChen
 */
public enum GapType {
    /**
     * 概念性缺口 - 不理解基本概念
     */
    CONCEPTUAL("conceptual", "概念性缺口"),

    /**
     * 程序性缺口 - 不知道如何操作/应用
     */
    PROCEDURAL("procedural", "程序性缺口"),

    /**
     * 边界性缺口 - 不清楚概念的边界/限制
     */
    BOUNDARY("boundary", "边界性缺口");

    private final String code;
    private final String description;

    GapType(String code, String description) {
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
     * 根据编码获取缺口类型
     *
     * @param code 编码
     * @return GapType 或 null
     */
    public static GapType fromCode(String code) {
        for (GapType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
