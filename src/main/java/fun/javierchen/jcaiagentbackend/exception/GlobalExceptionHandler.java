package fun.javierchen.jcaiagentbackend.exception;


import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 *
 * @author JavierChen
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object businessExceptionHandler(BusinessException e, HttpServletRequest request) {
        log.error("BusinessException", e);
        if (acceptsEventStream(request)) {
            String payload = "{\"code\":" + e.getCode() + ",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
            return ResponseEntity.status(400)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body("event: error\ndata: " + payload + "\n\n");
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Object runtimeExceptionHandler(RuntimeException e, HttpServletRequest request) {
        log.error("RuntimeException", e);
        if (acceptsEventStream(request)) {
            String payload = "{\"code\":" + ErrorCode.SYSTEM_ERROR.getCode() + ",\"message\":\"系统错误\"}";
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body("event: error\ndata: " + payload + "\n\n");
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    private boolean acceptsEventStream(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
