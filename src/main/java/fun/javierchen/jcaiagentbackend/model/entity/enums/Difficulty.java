package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 难度等级枚举
 * 定义题目的难度等级
 *
 * @author JavierChen
 */
public enum Difficulty {
    /**
     * 简单
     */
    EASY("easy", "简单", 1),

    /**
     * 中等
     */
    MEDIUM("medium", "中等", 2),

    /**
     * 困难
     */
    HARD("hard", "困难", 3);

    private final String code;
    private final String description;
    private final int level;

    Difficulty(String code, String description, int level) {
        this.code = code;
        this.description = description;
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getLevel() {
        return level;
    }

    /**
     * 根据编码获取难度
     *
     * @param code 编码
     * @return Difficulty 或 null
     */
    public static Difficulty fromCode(String code) {
        for (Difficulty difficulty : values()) {
            if (difficulty.code.equalsIgnoreCase(code)) {
                return difficulty;
            }
        }
        return null;
    }

    /**
     * 根据等级获取难度
     *
     * @param level 等级 (1-3)
     * @return Difficulty 或 MEDIUM
     */
    public static Difficulty fromLevel(int level) {
        for (Difficulty difficulty : values()) {
            if (difficulty.level == level) {
                return difficulty;
            }
        }
        return MEDIUM;
    }

    /**
     * 获取下一个更高的难度
     *
     * @return 更高难度或当前难度（如果已是最高）
     */
    public Difficulty harder() {
        return switch (this) {
            case EASY -> MEDIUM;
            case MEDIUM -> HARD;
            case HARD -> HARD;
        };
    }

    /**
     * 获取下一个更低的难度
     *
     * @return 更低难度或当前难度（如果已是最低）
     */
    public Difficulty easier() {
        return switch (this) {
            case EASY -> EASY;
            case MEDIUM -> EASY;
            case HARD -> MEDIUM;
        };
    }
}
