package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.agent.service.DisplayEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 仅输出展示事件的 SSE 控制器
 */
@RestController
@RequestMapping("/agent/display")
public class AgentDisplayController {
    private final DisplayEventStreamService displayEventStreamService;

    public AgentDisplayController(DisplayEventStreamService displayEventStreamService) {
        this.displayEventStreamService = displayEventStreamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // Controller 只面向 DisplayEvent，不感知 AgentEvent
    public SseEmitter stream(@RequestParam("sessionId") String sessionId,
                             @RequestParam("message") String message) {
        return displayEventStreamService.stream(sessionId, message);
    }
}
