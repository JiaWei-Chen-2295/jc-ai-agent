package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 概念掌握程度枚举
 * 定义用户对某个知识点的掌握程度
 *
 * @author JavierChen
 */
public enum ConceptMastery {
    /**
     * 已掌握
     */
    MASTERED("mastered", "已掌握"),

    /**
     * 部分掌握
     */
    PARTIAL("partial", "部分掌握"),

    /**
     * 未掌握
     */
    UNMASTERED("unmastered", "未掌握");

    private final String code;
    private final String description;

    ConceptMastery(String code, String description) {
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
     * 根据编码获取掌握程度
     *
     * @param code 编码
     * @return ConceptMastery 或 null
     */
    public static ConceptMastery fromCode(String code) {
        for (ConceptMastery mastery : values()) {
            if (mastery.code.equalsIgnoreCase(code)) {
                return mastery;
            }
        }
        return null;
    }

    /**
     * 判断是否需要补漏
     *
     * @return true 表示需要补漏
     */
    public boolean needsRemediation() {
        return this == PARTIAL || this == UNMASTERED;
    }

    /**
     * 判断是否完全掌握
     *
     * @return true 表示完全掌握
     */
    public boolean isFullyMastered() {
        return this == MASTERED;
    }
}
