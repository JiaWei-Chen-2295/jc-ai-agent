package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * 题目类型枚举
 * 定义测验题目的类型，支持多种题型以满足不同考查需求
 *
 * @author JavierChen
 */
public enum QuestionType {

    // ==================== 客观题 ====================

    /**
     * 单选题 - 从多个选项中选择一个正确答案
     * options: ["A", "B", "C", "D"]
     * correct_answer: "B"
     */
    SINGLE_CHOICE("single_choice", "单选题"),

    /**
     * 多选题 - 从多个选项中选择多个正确答案
     * options: ["A", "B", "C", "D"]
     * correct_answer: "A,C,D" (逗号分隔)
     */
    MULTIPLE_SELECT("multiple_select", "多选题"),

    /**
     * 判断题 - 判断陈述是否正确
     * options: null (固定为 正确/错误)
     * correct_answer: "TRUE" 或 "FALSE"
     */
    TRUE_FALSE("true_false", "判断题"),

    // ==================== 填空题 ====================

    /**
     * 填空题 - 填写一个或多个空白处的内容
     * options: null
     * correct_answer: "答案1;答案2" (多个空用分号分隔)
     * 注意: 支持多种正确答案，用 | 分隔，如 "Java|JAVA|java"
     */
    FILL_IN_BLANK("fill_in_blank", "填空题"),

    // ==================== 主观题 ====================

    /**
     * 简答题 - 简短回答问题
     * options: null
     * correct_answer: 参考答案 (AI 评分)
     */
    SHORT_ANSWER("short_answer", "简答题"),

    /**
     * 解释题 - 考查"为什么"，要求解释原理
     * options: null
     * correct_answer: 参考答案 (AI 评分)
     */
    EXPLANATION("explanation", "解释题"),

    // ==================== 综合题 ====================

    /**
     * 连线题 - 将左侧项与右侧项进行匹配
     * options: [{"left": "Java", "right": "面向对象"}, {"left": "Python", "right":
     * "脚本语言"}]
     * correct_answer: "1-A,2-B,3-C" (左侧序号-右侧字母)
     */
    MATCHING("matching", "连线题"),

    /**
     * 排序题 - 将给定项目按正确顺序排列
     * options: ["编译", "链接", "预处理", "运行"]
     * correct_answer: "3,1,2,4" (正确顺序的索引，从1开始)
     */
    ORDERING("ordering", "排序题"),

    /**
     * 代码补全题 - 补全代码片段
     * options: null
     * correct_answer: 正确的代码片段
     */
    CODE_COMPLETION("code_completion", "代码补全");

    private final String code;
    private final String description;

    QuestionType(String code, String description) {
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
     * 根据编码获取类型
     *
     * @param code 编码
     * @return QuestionType 或 null
     */
    public static QuestionType fromCode(String code) {
        for (QuestionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 是否需要选项
     *
     * @return true 表示需要选项 (如选择题、排序题、连线题)
     */
    public boolean requiresOptions() {
        return this == SINGLE_CHOICE
                || this == MULTIPLE_SELECT
                || this == MATCHING
                || this == ORDERING;
    }

    /**
     * 是否为客观题 (可自动评分)
     *
     * @return true 表示客观题
     */
    public boolean isObjective() {
        return this == SINGLE_CHOICE
                || this == MULTIPLE_SELECT
                || this == TRUE_FALSE
                || this == FILL_IN_BLANK
                || this == MATCHING
                || this == ORDERING;
    }

    /**
     * 是否为主观题 (需要 AI 评分)
     *
     * @return true 表示主观题
     */
    public boolean isSubjective() {
        return this == SHORT_ANSWER
                || this == EXPLANATION
                || this == CODE_COMPLETION;
    }

    /**
     * 是否支持部分得分
     *
     * @return true 表示支持部分得分
     */
    public boolean supportsPartialScore() {
        return this == MULTIPLE_SELECT
                || this == FILL_IN_BLANK
                || this == MATCHING
                || this == ORDERING
                || this == SHORT_ANSWER
                || this == EXPLANATION
                || this == CODE_COMPLETION;
    }
}
