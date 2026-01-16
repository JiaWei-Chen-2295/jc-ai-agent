package fun.javierchen.jcaiagentbackend.agent.service;

import fun.javierchen.jcaiagentbackend.agent.adapter.AgentEventDisplayAdapter;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayEvent;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;
import fun.javierchen.jcaiagentbackend.agent.runtime.AgentRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 默认 SSE 展示流服务
 */
@Service
//@ConditionalOnMissingBean(DisplayEventStreamService.class)
public class DefaultDisplayEventStreamService implements DisplayEventStreamService {
    private static final long DEFAULT_TIMEOUT_MILLIS = 3 * 60 * 1000L;

    private final AgentRuntime agentRuntime;
    private final AgentEventDisplayAdapter adapter;

    public DefaultDisplayEventStreamService(AgentRuntime agentRuntime, AgentEventDisplayAdapter adapter) {
        this.agentRuntime = agentRuntime;
        this.adapter = adapter;
    }

    @Override
    // 顺序发送展示事件，完成后关闭流
    public SseEmitter stream(String sessionId, String userInput) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
        Iterable<AgentEvent<?>> events = agentRuntime.run(sessionId, userInput);
        if (events == null) {
            emitter.complete();
            return emitter;
        }
        for (AgentEvent<?> event : events) {
            List<DisplayEvent> displayEvents = adapter.adapt(event);
            if (displayEvents == null || displayEvents.isEmpty()) {
                continue;
            }
            for (DisplayEvent displayEvent : displayEvents) {
                try {
                    emitter.send(displayEvent);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    return emitter;
                }
            }
        }
        emitter.complete();
        return emitter;
    }
}
