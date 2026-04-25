package fun.javierchen.jcaiagentbackend.voice.model;

import java.time.Instant;

public record VoiceEventEnvelope(
        String sessionId,
        String turnId,
        Instant timestamp,
        String type,
        Object payload
) {
}