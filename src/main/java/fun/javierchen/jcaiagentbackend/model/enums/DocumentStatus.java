package fun.javierchen.jcaiagentbackend.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档处理状态枚举
 * 用于控制文档向量化的生命周期
 * 对应表 study_friend_document 的 status 字段
 *
 * @author JavierChen
 */
@Schema(description = "文档处理状态", example = "INDEXED")
public enum DocumentStatus {

    /**
     * 文件已上传，等待处理
     */
    UPLOADED("UPLOADED", "已上传"),

    /**
     * 正在解析/向量化中
     */
    INDEXING("INDEXING", "索引中"),

    /**
     * 已入库，可检索
     */
    INDEXED("INDEXED", "已索引"),

    /**
     * 处理失败（可重试）
     */
    FAILED("FAILED", "处理失败");

    private final String code;
    private final String description;

    DocumentStatus(String code, String description) {
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
     * 根据 code 获取枚举
     */
    public static DocumentStatus fromCode(String code) {
        for (DocumentStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文档状态: " + code);
    }
}
