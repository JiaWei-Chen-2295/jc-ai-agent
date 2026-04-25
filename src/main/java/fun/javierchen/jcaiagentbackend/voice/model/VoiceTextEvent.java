package fun.javierchen.jcaiagentbackend.voice.model;

import java.time.Instant;

public record VoiceTextEvent(
        String sessionId,
        String chatId,
        String turnId,
        long sequence,
        Instant timestamp,
        String type,
        Object payload
) {
}