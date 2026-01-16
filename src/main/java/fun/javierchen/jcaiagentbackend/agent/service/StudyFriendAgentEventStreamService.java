package fun.javierchen.jcaiagentbackend.agent.service;

import fun.javierchen.jcaiagentbackend.agent.adapter.AgentEventDisplayAdapter;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayEvent;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayFormats;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayStages;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEventMeta;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEventStage;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEventTypes;
import fun.javierchen.jcaiagentbackend.agent.event.BaseAgentEvent;
import fun.javierchen.jcaiagentbackend.agent.event.OutputDeltaPayload;
import fun.javierchen.jcaiagentbackend.app.StudyFriend;
import fun.javierchen.jcaiagentbackend.service.StudyFriendChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * StudyFriend 的 AgentEvent 流式输出服务
 */
@Slf4j
@Service
public class StudyFriendAgentEventStreamService {
    private static final long DEFAULT_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    private static final long INITIAL_OUTPUT_TIMEOUT_MILLIS = 15_000L;

    private enum StreamState {
        NEW,
        STREAMING,
        FALLBACK,
        DONE
    }

    private final StudyFriend studyFriend;
    private final AgentEventDisplayAdapter adapter;
    private final StudyFriendChatService chatService;

    public StudyFriendAgentEventStreamService(StudyFriend studyFriend,
                                              AgentEventDisplayAdapter adapter,
                                              StudyFriendChatService chatService) {
        this.studyFriend = studyFriend;
        this.adapter = adapter;
        this.chatService = chatService;
    }

    /**
     * 基于 AgentEvent 生成展示事件并通过 SSE 推送
     */
    public SseEmitter stream(Long tenantId, Long userId, String chatId, String chatMessage, boolean enableTool) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicReference<StreamState> state = new AtomicReference<StreamState>(StreamState.NEW);
        AtomicReference<Disposable> streamRef = new AtomicReference<Disposable>();
        AtomicReference<Disposable> initialTimeoutRef = new AtomicReference<Disposable>();
        AtomicReference<Disposable> fallbackCallRef = new AtomicReference<Disposable>();
        sendEvent(emitter, buildEvent(AgentEventTypes.THINKING_START, AgentEventStage.THINKING, null, chatId));
        sendEvent(emitter, buildEvent(AgentEventTypes.OUTPUT_START, AgentEventStage.OUTPUT, null, chatId));
        Flux<String> stream = enableTool
                ? studyFriend.doChatWithRAGStreamTool(chatMessage, chatId, tenantId)
                : studyFriend.doChatWithRAGStream(chatMessage, chatId, tenantId);
        Disposable disposable = stream.subscribe(
                chunk -> {
                    if (!enterStreaming(state, initialTimeoutRef, fallbackCallRef)) {
                        return;
                    }
                    if (chunk == null || chunk.isEmpty()) {
                        return;
                    }
                    assistantBuffer.append(chunk);
                    sendEvent(emitter, buildEvent(
                            AgentEventTypes.OUTPUT_DELTA,
                            AgentEventStage.OUTPUT,
                            new OutputDeltaPayload(chunk),
                            chatId
                    ));
                },
                error -> {
                    if (!markDone(state)) {
                        return;
                    }
                    cleanupFallback(initialTimeoutRef, fallbackCallRef);
                    log.error("AgentEvent stream error", error);
                    sendDisplayStatus(emitter, "Stream error: " + safeMessage(error));
                    emitter.complete();
                },
                () -> {
                    if (state.get() == StreamState.NEW) {
                        // 流式无输出且已完成，直接降级为一次性回复
                        cleanupInitialTimeout(initialTimeoutRef);
                        triggerFallback(emitter, tenantId, userId, chatId, chatMessage, enableTool, assistantBuffer, state, streamRef, fallbackCallRef);
                        return;
                    }
                    if (state.get() == StreamState.STREAMING) {
                        cleanupFallback(initialTimeoutRef, fallbackCallRef);
                        completeStream(emitter, tenantId, userId, chatId, assistantBuffer);
                        state.set(StreamState.DONE);
                    }
                }
        );
        streamRef.set(disposable);
        Disposable initialTimeoutDisposable = Mono.delay(Duration.ofMillis(INITIAL_OUTPUT_TIMEOUT_MILLIS))
                .subscribe(ignore -> triggerFallback(emitter, tenantId, userId, chatId, chatMessage, enableTool,
                        assistantBuffer, state, streamRef, fallbackCallRef));
        initialTimeoutRef.set(initialTimeoutDisposable);
        // SSE 结束后释放订阅
        emitter.onCompletion(() -> cleanup(disposable, initialTimeoutRef, fallbackCallRef, streamRef));
        emitter.onTimeout(() -> cleanup(disposable, initialTimeoutRef, fallbackCallRef, streamRef));
        return emitter;
    }

    // 构造带有 traceId 的 AgentEvent
    private AgentEvent<?> buildEvent(String type, AgentEventStage stage, Object payload, String traceId) {
        AgentEventMeta meta = new AgentEventMeta(traceId, System.currentTimeMillis());
        return BaseAgentEvent.of(type, stage, payload, meta);
    }

    // 适配后发送 DisplayEvent
    private void sendEvent(SseEmitter emitter, AgentEvent<?> event) {
        List<DisplayEvent> displayEvents = adapter.adapt(event);
        if (displayEvents == null || displayEvents.isEmpty()) {
            return;
        }
        for (DisplayEvent displayEvent : displayEvents) {
            try {
                emitter.send(SseEmitter.event()
                        .name(displayEvent.getType())
                        .data(displayEvent, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.warn("SSE send failed: type={}, stage={}, format={}, delta={}",
                        displayEvent.getType(),
                        displayEvent.getStage(),
                        displayEvent.getFormat(),
                        displayEvent.isDelta(),
                        e);
                emitter.complete();
                return;
            }
        }
    }

    private boolean enterStreaming(AtomicReference<StreamState> state,
                                   AtomicReference<Disposable> initialTimeoutRef,
                                   AtomicReference<Disposable> fallbackCallRef) {
        StreamState current = state.get();
        if (current == StreamState.DONE) {
            return false;
        }
        if (current == StreamState.NEW && state.compareAndSet(StreamState.NEW, StreamState.STREAMING)) {
            cleanupFallback(initialTimeoutRef, fallbackCallRef);
        } else if (current == StreamState.FALLBACK && state.compareAndSet(StreamState.FALLBACK, StreamState.STREAMING)) {
            cleanupInitialTimeout(initialTimeoutRef);
            Disposable fallbackCall = fallbackCallRef.getAndSet(null);
            if (fallbackCall != null) {
                fallbackCall.dispose();
            }
        }
        return state.get() == StreamState.STREAMING;
    }

    private boolean markDone(AtomicReference<StreamState> state) {
        StreamState current = state.get();
        if (current == StreamState.DONE || current == StreamState.FALLBACK) {
            return false;
        }
        state.set(StreamState.DONE);
        return true;
    }

    private void triggerFallback(SseEmitter emitter,
                                 Long tenantId,
                                 Long userId,
                                 String chatId,
                                 String chatMessage,
                                 boolean enableTool,
                                 StringBuilder assistantBuffer,
                                 AtomicReference<StreamState> state,
                                 AtomicReference<Disposable> streamRef,
                                 AtomicReference<Disposable> fallbackCallRef) {
        if (!state.compareAndSet(StreamState.NEW, StreamState.FALLBACK)) {
            return;
        }
        Disposable fallbackCall = Mono.fromCallable(() -> safeSyncReply(chatMessage, chatId, enableTool, tenantId))
                 .subscribeOn(Schedulers.boundedElastic())
                 .subscribe(content -> {
                     // 如果流式输出已开始，则忽略降级结果，避免双输出
                     if (!state.compareAndSet(StreamState.FALLBACK, StreamState.DONE)) {
                         return;
                     }
                     Disposable disposable = streamRef.getAndSet(null);
                     if (disposable != null) {
                         disposable.dispose();
                     }
                     if (content != null && !content.isEmpty()) {
                         assistantBuffer.append(content);
                         sendEvent(emitter, buildEvent(
                                 AgentEventTypes.OUTPUT_DELTA,
                                 AgentEventStage.OUTPUT,
                                 new OutputDeltaPayload(content),
                                 chatId
                         ));
                     }
                     completeStream(emitter, tenantId, userId, chatId, assistantBuffer);
                 }, error -> {
                     // 如果流式输出已开始，则忽略降级错误，避免干扰正常输出
                     if (!state.compareAndSet(StreamState.FALLBACK, StreamState.DONE)) {
                         return;
                     }
                     Disposable disposable = streamRef.getAndSet(null);
                     if (disposable != null) {
                         disposable.dispose();
                     }
                     log.error("AgentEvent fallback error", error);
                     sendDisplayStatus(emitter, "Fallback error: " + safeMessage(error));
                     emitter.complete();
                 });
        fallbackCallRef.set(fallbackCall);
    }

    private String safeSyncReply(String chatMessage, String chatId, boolean enableTool, Long tenantId) {
        String content = enableTool
                ? studyFriend.doChatWithTools(chatMessage, chatId, tenantId)
                : studyFriend.doChatWithRAG(chatMessage, chatId, tenantId);
        return content == null ? "" : content;
    }

    private void completeStream(SseEmitter emitter,
                                Long tenantId,
                                Long userId,
                                String chatId,
                                StringBuilder assistantBuffer) {
        sendEvent(emitter, buildEvent(AgentEventTypes.OUTPUT_COMPLETE, AgentEventStage.OUTPUT, null, chatId));
        chatService.appendAssistantMessage(chatId, tenantId, userId, assistantBuffer.toString());
        emitter.complete();
    }

    private void cleanup(Disposable disposable,
                         AtomicReference<Disposable> initialTimeoutRef,
                         AtomicReference<Disposable> fallbackCallRef,
                         AtomicReference<Disposable> streamRef) {
        cleanupFallback(initialTimeoutRef, fallbackCallRef);
        Disposable stream = streamRef.getAndSet(null);
        if (stream != null) {
            stream.dispose();
        } else if (disposable != null) {
            disposable.dispose();
        }
    }

    private void cleanupInitialTimeout(AtomicReference<Disposable> initialTimeoutRef) {
        Disposable initialTimeout = initialTimeoutRef.getAndSet(null);
        if (initialTimeout != null) {
            initialTimeout.dispose();
        }
    }

    private void cleanupFallback(AtomicReference<Disposable> initialTimeoutRef,
                                 AtomicReference<Disposable> fallbackCallRef) {
        cleanupInitialTimeout(initialTimeoutRef);
        Disposable fallbackCall = fallbackCallRef.getAndSet(null);
        if (fallbackCall != null) {
            fallbackCall.dispose();
        }
    }

    private void sendDisplayStatus(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event()
                    .name(DisplayEvent.TYPE_DISPLAY)
                    .data(DisplayEvent.of(DisplayStages.OUTPUT, DisplayFormats.STATUS, content, false),
                            MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
        }
    }

    private String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String message = t.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return t.getClass().getSimpleName();
    }
}
