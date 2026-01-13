package fun.javierchen.jcaiagentbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "健康检查", description = "基础探活接口")
public class HealthController {

    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "健康检查", description = "用于探活或部署验证",
            responses = {
                    @ApiResponse(responseCode = "200", description = "服务正常，返回 OK")
            })
    public String health() {
        return "OK";
    }

}
