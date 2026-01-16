package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * 思考过程中的进度信息
 */
public class ThinkingProgressPayload {
    private final String message;

    public ThinkingProgressPayload(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
