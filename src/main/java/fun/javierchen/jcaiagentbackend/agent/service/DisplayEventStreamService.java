package fun.javierchen.jcaiagentbackend.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DisplayEvent 流式输出服务
 */
public interface DisplayEventStreamService {
    SseEmitter stream(String sessionId, String userInput);
}
