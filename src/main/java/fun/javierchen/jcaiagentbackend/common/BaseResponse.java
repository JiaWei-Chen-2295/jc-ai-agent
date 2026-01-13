package fun.javierchen.jcaiagentbackend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回类
 *
 * @param <T>
 * @author JavierChen
 */
@Data
@Schema(description = "通用响应包装，所有接口保持相同结构")
public class BaseResponse<T> implements Serializable {

    @Schema(description = "业务状态码，0 表示成功，其余表示失败", example = "0")
    private int code;

    @Schema(description = "真实业务数据载荷，可以是对象、列表或字符串")
    private T data;

    @Schema(description = "人类可读的补充信息，失败时包含错误原因", example = "ok")
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
