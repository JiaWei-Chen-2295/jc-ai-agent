package fun.javierchen.jcaiagentbackend.voice.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.service.UserService;
import fun.javierchen.jcaiagentbackend.voice.service.VoiceTextStreamService;
import fun.javierchen.jcaiagentbackend.voice.service.VoiceTurnOrchestrator;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceSessionRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
@Tag(name = "实时语音", description = "语音会话的文本 SSE 与回合控制接口")
public class VoiceController {

    private final UserService userService;
    private final VoiceSessionRegistry voiceSessionRegistry;
    private final VoiceTurnOrchestrator voiceTurnOrchestrator;
    private final VoiceTextStreamService voiceTextStreamService;

    @PostMapping(value = "/turn/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start a voice turn", description = "Starts a new turn on the caller's active voice WebSocket session.")
    public BaseResponse<VoiceTurnStartResponse> startTurn(@RequestBody VoiceTurnStartRequest request,
                                                          HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        var sessionContext = voiceSessionRegistry.getRequiredActiveSessionByUserId(loginUser.getId());
        var turnContext = voiceTurnOrchestrator.startTurn(sessionContext, request.toCommand());
        return ResultUtils.success(new VoiceTurnStartResponse(sessionContext.getWebsocketSessionId(), turnContext.getTurnId()));
    }

    @PostMapping(value = "/turn/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Interrupt a voice turn", description = "Interrupts the current or specified voice turn.")
    public BaseResponse<Boolean> stopTurn(@RequestBody VoiceTurnStopRequest request,
                                          HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        var sessionContext = voiceSessionRegistry.getRequiredActiveSessionByUserId(loginUser.getId());
        voiceTurnOrchestrator.interruptTurn(sessionContext, request.turnId(), "Interrupted through REST endpoint");
        return ResultUtils.success(Boolean.TRUE);
    }

    @GetMapping(value = "/turn/{turnId}/text/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream voice turn text", description = "Streams normalized per-turn text events over SSE.")
    public SseEmitter streamTurnText(@PathVariable("turnId") String turnId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        if (!voiceTextStreamService.canAccessTurn(turnId, loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Voice turn not found");
        }
        return voiceTextStreamService.openStream(turnId);
    }

    public record VoiceTurnStartRequest(
            String turnId,
            String chatId,
            String transcript,
            String messageId,
            Boolean webSearchEnabled
    ) {
        private fun.javierchen.jcaiagentbackend.voice.model.VoiceTurnStartCommand toCommand() {
            return new fun.javierchen.jcaiagentbackend.voice.model.VoiceTurnStartCommand(
                    turnId,
                    chatId,
                    transcript,
                    messageId,
                    webSearchEnabled
            );
        }
    }

    public record VoiceTurnStopRequest(String turnId) {
    }

    public record VoiceTurnStartResponse(String sessionId, String turnId) {
    }
}