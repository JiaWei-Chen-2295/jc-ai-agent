package fun.javierchen.jcaiagentbackend.voice.model;

public record TtsStatePayload(String phase, String mimeType, boolean skipped) {
}