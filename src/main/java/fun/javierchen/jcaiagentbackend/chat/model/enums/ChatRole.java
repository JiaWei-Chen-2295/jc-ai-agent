package fun.javierchen.jcaiagentbackend.chat.model.enums;

/**
 * Chat message roles.
 */
public enum ChatRole {

    USER("user"),
    ASSISTANT("assistant");

    private final String code;

    ChatRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
