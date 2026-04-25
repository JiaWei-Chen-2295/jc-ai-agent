package fun.javierchen.jcaiagentbackend.voice.session;

import fun.javierchen.jcaiagentbackend.voice.model.VoiceSessionState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

@Getter
public class VoiceSessionContext {

    private final String websocketSessionId;
    private final String httpSessionId;
    private final Long userId;
    private final Long tenantId;

    @Setter
    private WebSocketSession webSocketSession;

    @Setter
    private String codec;

    @Setter
    private String activeTurnId;

    @Setter
    private VoiceSessionState sessionState;

    private final Instant connectedAt;

    private volatile Instant lastSeenAt;

    @Setter
    private Instant disconnectedAt;

    public VoiceSessionContext(String websocketSessionId, String httpSessionId, Long userId, Long tenantId,
                               WebSocketSession webSocketSession, String codec, VoiceSessionState sessionState) {
        this.websocketSessionId = websocketSessionId;
        this.httpSessionId = httpSessionId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.webSocketSession = webSocketSession;
        this.codec = codec;
        this.sessionState = sessionState;
        this.connectedAt = Instant.now();
        this.lastSeenAt = this.connectedAt;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public boolean isOpen() {
        return webSocketSession != null && webSocketSession.isOpen();
    }
}