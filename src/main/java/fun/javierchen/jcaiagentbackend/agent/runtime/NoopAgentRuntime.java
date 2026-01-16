package fun.javierchen.jcaiagentbackend.agent.runtime;

import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 缺省空实现，用于占位
 */
@Component
//@ConditionalOnMissingBean(AgentRuntime.class)
public class NoopAgentRuntime implements AgentRuntime {

    @Override
    // 返回空事件流
    public Iterable<AgentEvent<?>> run(String sessionId, String userInput) {
        return Collections.emptyList();
    }
}
