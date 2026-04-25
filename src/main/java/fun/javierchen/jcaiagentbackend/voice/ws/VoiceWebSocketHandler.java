package fun.javierchen.jcaiagentbackend.voice.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceSessionHandshakeInterceptor;
import fun.javierchen.jcaiagentbackend.voice.model.ErrorPayload;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceControlMessage;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceEventEnvelope;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceSessionState;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceTurnStartCommand;
import fun.javierchen.jcaiagentbackend.voice.service.VoiceTurnOrchestrator;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceSessionContext;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.EOFException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private final VoiceSessionRegistry voiceSessionRegistry;
    private final VoiceTurnOrchestrator voiceTurnOrchestrator;
    private final VoiceProperties voiceProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(VoiceSessionHandshakeInterceptor.ATTR_USER_ID);
        Long tenantId = (Long) session.getAttributes().get(VoiceSessionHandshakeInterceptor.ATTR_TENANT_ID);
        String httpSessionId = (String) session.getAttributes().get(VoiceSessionHandshakeInterceptor.ATTR_HTTP_SESSION_ID);

        WebSocketSession outboundSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);
        VoiceSessionContext context = new VoiceSessionContext(
                session.getId(),
                httpSessionId,
                userId,
                tenantId,
                outboundSession,
                voiceProperties.getCodec(),
                VoiceSessionState.ready
        );
        voiceSessionRegistry.register(context);
        voiceTurnOrchestrator.emitSessionReady(context);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        VoiceSessionContext context = voiceSessionRegistry.findBySocketSessionId(session.getId());
        if (context == null) {
            return;
        }
        context.touch();
        VoiceControlMessage controlMessage = objectMapper.readValue(message.getPayload(), VoiceControlMessage.class);
        switch (StringUtils.defaultString(controlMessage.type())) {
            case "session.ping" -> session.sendMessage(new PongMessage(ByteBuffer.wrap(new byte[0])));
            case "turn.start" -> voiceTurnOrchestrator.startTurn(context, new VoiceTurnStartCommand(
                    controlMessage.turnId(),
                    controlMessage.chatId(),
                    controlMessage.transcript(),
                    controlMessage.messageId(),
                    controlMessage.webSearchEnabled()
            ));
            case "turn.commit_text" -> voiceTurnOrchestrator.commitTranscript(context, controlMessage.turnId(), controlMessage.transcript());
            case "turn.stop" -> voiceTurnOrchestrator.stopTurn(context, controlMessage.turnId());
            case "turn.interrupt" -> voiceTurnOrchestrator.interruptTurn(context, controlMessage.turnId(), "Interrupted by client request");
            default -> emitError(context, controlMessage.turnId(), "VOICE_CONTROL_UNSUPPORTED", "Unsupported voice control message: " + controlMessage.type(), true);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        VoiceSessionContext context = voiceSessionRegistry.findBySocketSessionId(session.getId());
        if (context == null) {
            return;
        }
        context.touch();
        byte[] payload = new byte[message.getPayloadLength()];
        message.getPayload().get(payload);
        voiceTurnOrchestrator.handleAudioChunk(context, payload);
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        VoiceSessionContext context = voiceSessionRegistry.findBySocketSessionId(session.getId());
        if (context != null) {
            context.touch();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if (isExpectedTransportClose(exception)) {
            log.info("Voice websocket closed by peer: sessionId={}, reason={}", session.getId(), safeMessage(exception));
            return;
        }
        VoiceSessionContext context = voiceSessionRegistry.findBySocketSessionId(session.getId());
        if (context != null) {
            emitError(context, context.getActiveTurnId(), "VOICE_SOCKET_TRANSPORT_ERROR", safeMessage(exception), true);
        }
        log.warn("Voice websocket transport error: sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        VoiceSessionContext context = voiceSessionRegistry.findBySocketSessionId(session.getId());
        if (context == null) {
            return;
        }
        voiceTurnOrchestrator.handleSocketClosed(context, status.getReason());
        voiceSessionRegistry.markDisconnected(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void emitError(VoiceSessionContext context, String turnId, String code, String message, boolean recoverable) {
        voiceSessionRegistry.sendEvent(context,
                new VoiceEventEnvelope(context.getWebsocketSessionId(), turnId, Instant.now(), "error", new ErrorPayload(code, message, recoverable)));
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        return StringUtils.defaultIfBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
    }

    private boolean isExpectedTransportClose(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof EOFException) {
                return true;
            }
            if (current instanceof SocketException) {
                String message = StringUtils.defaultString(current.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("connection reset")
                        || message.contains("broken pipe")
                        || message.contains("connection aborted")
                        || message.contains("forcibly closed")) {
                    return true;
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}