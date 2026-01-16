package fun.javierchen.jcaiagentbackend.agent.adapter;

import fun.javierchen.jcaiagentbackend.agent.display.DisplayEvent;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;

import java.util.List;

/**
 * AgentEvent 到 DisplayEvent 的适配接口
 */
public interface AgentEventDisplayAdapter {
    List<DisplayEvent> adapt(AgentEvent<?> event);
}
