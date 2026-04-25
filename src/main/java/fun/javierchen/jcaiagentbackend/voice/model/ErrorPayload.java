package fun.javierchen.jcaiagentbackend.voice.model;

public record ErrorPayload(String code, String message, boolean recoverable) {
}