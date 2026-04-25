package fun.javierchen.jcaiagentbackend.voice.model;

public record TurnStatePayload(
        String chatId,
        TurnState state,
        TurnEndReason endReason
) {
}