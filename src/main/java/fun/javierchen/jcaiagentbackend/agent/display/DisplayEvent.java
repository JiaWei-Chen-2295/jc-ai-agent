package fun.javierchen.jcaiagentbackend.agent.display;

/**
 * 前端展示事件
 */
public class DisplayEvent {
    public static final String TYPE_DISPLAY = "display";

    private final String type;
    private final String stage;
    private final String format;
    private final String content;
    private final boolean delta;

    public DisplayEvent(String stage, String format, String content, boolean delta) {
        this.type = TYPE_DISPLAY;
        this.stage = stage;
        this.format = format;
        this.content = content;
        this.delta = delta;
    }

    public static DisplayEvent of(String stage, String format, String content, boolean delta) {
        return new DisplayEvent(stage, format, content, delta);
    }

    public String getType() {
        return type;
    }

    public String getStage() {
        return stage;
    }

    public String getFormat() {
        return format;
    }

    public String getContent() {
        return content;
    }

    public boolean isDelta() {
        return delta;
    }
}
