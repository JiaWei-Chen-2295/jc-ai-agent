package fun.javierchen.jcaiagentbackend.voice.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceEventEnvelope;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceSessionRegistry {

    private final Map<String, VoiceSessionContext> sessionsBySocketId = new ConcurrentHashMap<>();
    private final Map<Long, VoiceSessionContext> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, VoiceTurnContext> turnsById = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final VoiceProperties voiceProperties;

    public VoiceSessionContext register(VoiceSessionContext context) {
        VoiceSessionContext previous = sessionsByUserId.put(context.getUserId(), context);
        if (previous != null && previous.isOpen() && !previous.getWebsocketSessionId().equals(context.getWebsocketSessionId())) {
            try {
                previous.getWebSocketSession().close(CloseStatus.NORMAL.withReason("Replaced by newer voice session"));
            } catch (IOException e) {
                log.debug("Failed to close replaced voice session", e);
            }
            sessionsBySocketId.remove(previous.getWebsocketSessionId());
        }
        sessionsBySocketId.put(context.getWebsocketSessionId(), context);
        return context;
    }

    public VoiceSessionContext findBySocketSessionId(String websocketSessionId) {
        return sessionsBySocketId.get(websocketSessionId);
    }

    public VoiceSessionContext findByUserId(Long userId) {
        return sessionsByUserId.get(userId);
    }

    public VoiceSessionContext getRequiredActiveSessionByUserId(Long userId) {
        VoiceSessionContext context = sessionsByUserId.get(userId);
        if (context == null || !context.isOpen()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Open the voice WebSocket before starting a turn");
        }
        return context;
    }

    public void markDisconnected(String websocketSessionId) {
        VoiceSessionContext context = sessionsBySocketId.get(websocketSessionId);
        if (context == null) {
            return;
        }
        context.setSessionState(VoiceSessionState.disconnected);
        context.setDisconnectedAt(Instant.now());
        context.setWebSocketSession(null);
        context.touch();
    }

    public void remove(String websocketSessionId) {
        VoiceSessionContext context = sessionsBySocketId.remove(websocketSessionId);
        if (context == null) {
            return;
        }
        VoiceSessionContext current = sessionsByUserId.get(context.getUserId());
        if (current != null && current.getWebsocketSessionId().equals(websocketSessionId)) {
            sessionsByUserId.remove(context.getUserId());
        }
    }

    public void registerTurn(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
        sessionContext.setActiveTurnId(turnContext.getTurnId());
        sessionContext.touch();
        turnsById.put(turnContext.getTurnId(), turnContext);
    }

    public VoiceTurnContext findTurn(String turnId) {
        return turnsById.get(turnId);
    }

    public void clearTurn(VoiceTurnContext turnContext) {
        turnsById.remove(turnContext.getTurnId());
        VoiceSessionContext sessionContext = sessionsBySocketId.get(turnContext.getSessionId());
        if (sessionContext != null && turnContext.getTurnId().equals(sessionContext.getActiveTurnId())) {
            sessionContext.setActiveTurnId(null);
        }
    }

    public void touch(VoiceSessionContext context) {
        context.touch();
    }

    public void sendEvent(VoiceSessionContext context, VoiceEventEnvelope envelope) {
        if (context == null || !context.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            context.getWebSocketSession().sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("Failed to send voice JSON event: sessionId={}, type={}", context.getWebsocketSessionId(), envelope.type(), e);
        }
    }

    public void sendBinary(VoiceSessionContext context, byte[] payload) {
        if (context == null || !context.isOpen() || payload == null || payload.length == 0) {
            return;
        }
        try {
            context.getWebSocketSession().sendMessage(new BinaryMessage(payload));
        } catch (IOException e) {
            log.warn("Failed to send voice binary event: sessionId={}", context.getWebsocketSessionId(), e);
        }
    }

    @Scheduled(fixedDelayString = "#{@voiceProperties.staleEvictionInterval.toMillis()}")
    public void evictStaleSessions() {
        Instant now = Instant.now();
        Duration reconnectGrace = voiceProperties.getReconnectGracePeriod();
        Duration idleTimeout = voiceProperties.getSessionIdleTimeout();
        for (VoiceSessionContext context : sessionsBySocketId.values()) {
            boolean shouldEvictDisconnected = context.getSessionState() == VoiceSessionState.disconnected
                    && context.getDisconnectedAt() != null
                    && context.getDisconnectedAt().plus(reconnectGrace).isBefore(now);
            boolean shouldEvictIdle = context.getLastSeenAt().plus(idleTimeout).isBefore(now);
            if (!shouldEvictDisconnected && !shouldEvictIdle) {
                continue;
            }
            remove(context.getWebsocketSessionId());
        }
    }
}