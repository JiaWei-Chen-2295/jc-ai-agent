package fun.javierchen.jcaiagentbackend.agent.runtime;

import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;

/**
 * Agent 运行时入口
 */
public interface AgentRuntime {
    Iterable<AgentEvent<?>> run(String sessionId, String userInput);
}
