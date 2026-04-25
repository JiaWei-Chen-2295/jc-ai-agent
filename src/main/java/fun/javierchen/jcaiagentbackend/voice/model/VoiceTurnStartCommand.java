package fun.javierchen.jcaiagentbackend.voice.model;

public record VoiceTurnStartCommand(
        String turnId,
        String chatId,
        String transcript,
        String messageId,
        Boolean webSearchEnabled
) {
}