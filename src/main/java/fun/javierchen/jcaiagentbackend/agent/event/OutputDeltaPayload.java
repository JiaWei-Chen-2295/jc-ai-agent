package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * 增量输出载荷
 */
public class OutputDeltaPayload {
    private final String delta;

    public OutputDeltaPayload(String delta) {
        this.delta = delta;
    }

    public String getDelta() {
        return delta;
    }
}
