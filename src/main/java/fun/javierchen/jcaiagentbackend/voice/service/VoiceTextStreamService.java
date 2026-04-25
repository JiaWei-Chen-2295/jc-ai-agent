package fun.javierchen.jcaiagentbackend.voice.service;

import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.model.TurnState;
import fun.javierchen.jcaiagentbackend.voice.model.TurnStatePayload;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceTextEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTextStreamService {

    private final Map<String, TurnTextChannel> channels = new ConcurrentHashMap<>();
    private final VoiceProperties voiceProperties;

    public void registerTurn(String sessionId, Long userId, String chatId, String turnId) {
        channels.computeIfAbsent(turnId, key -> new TurnTextChannel(sessionId, userId, chatId, turnId));
    }

    public boolean canAccessTurn(String turnId, Long userId) {
        TurnTextChannel channel = channels.get(turnId);
        return channel != null && Objects.equals(channel.userId, userId);
    }

    public SseEmitter openStream(String turnId) {
        TurnTextChannel channel = channels.get(turnId);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown voice turn: " + turnId);
        }
        SseEmitter emitter = new SseEmitter(voiceProperties.getTextStreamTimeout().toMillis());
        channel.emitters.add(emitter);
        emitter.onTimeout(() -> channel.emitters.remove(emitter));
        emitter.onCompletion(() -> channel.emitters.remove(emitter));

        List<VoiceTextEvent> backlog = channel.snapshot();
        for (VoiceTextEvent event : backlog) {
            send(emitter, event, false);
        }
        if (channel.closed) {
            emitter.complete();
        }
        return emitter;
    }

    public void publish(String turnId, String type, Object payload) {
        TurnTextChannel channel = channels.get(turnId);
        if (channel == null) {
            return;
        }
        channel.lastEventAt = Instant.now();
        VoiceTextEvent event = new VoiceTextEvent(
                channel.sessionId,
                channel.chatId,
                channel.turnId,
                channel.sequence.incrementAndGet(),
                channel.lastEventAt,
                type,
                payload
        );
        channel.events.add(event);
        for (SseEmitter emitter : channel.emitters) {
            send(emitter, event, true);
        }
        if ("turn_state".equals(type)
                && payload instanceof TurnStatePayload turnStatePayload
                && turnStatePayload.state() == TurnState.ended) {
            channel.closed = true;
            completeAll(channel.emitters);
        }
    }

    @Scheduled(fixedDelayString = "#{@voiceProperties.staleEvictionInterval.toMillis()}")
    public void evictClosedChannels() {
        Instant threshold = Instant.now().minus(voiceProperties.getReconnectGracePeriod());
        channels.entrySet().removeIf(entry -> entry.getValue().closed && entry.getValue().lastEventAt.isBefore(threshold));
    }

    private void send(SseEmitter emitter, VoiceTextEvent event, boolean removeOnFailure) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            if (removeOnFailure) {
                emitter.complete();
            }
        }
    }

    private void completeAll(List<SseEmitter> emitters) {
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
    }

    private static final class TurnTextChannel {
        private final String sessionId;
        private final Long userId;
        private final String chatId;
        private final String turnId;
        private final AtomicLong sequence = new AtomicLong();
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final List<VoiceTextEvent> events = new CopyOnWriteArrayList<>();
        private volatile boolean closed;
        private volatile Instant lastEventAt = Instant.now();

        private TurnTextChannel(String sessionId, Long userId, String chatId, String turnId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.chatId = chatId;
            this.turnId = turnId;
        }

        private List<VoiceTextEvent> snapshot() {
            this.lastEventAt = Instant.now();
            return new ArrayList<>(events);
        }
    }
}