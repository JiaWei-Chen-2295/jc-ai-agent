package fun.javierchen.jcaiagentbackend.voice.model;

public record VoiceControlMessage(
        String type,
        String turnId,
        String chatId,
        String transcript,
        String messageId,
        Boolean webSearchEnabled
) {
}