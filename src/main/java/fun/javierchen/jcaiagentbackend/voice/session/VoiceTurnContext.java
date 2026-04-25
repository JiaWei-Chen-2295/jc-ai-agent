package fun.javierchen.jcaiagentbackend.voice.session;

import fun.javierchen.jcaiagentbackend.voice.model.TurnEndReason;
import fun.javierchen.jcaiagentbackend.voice.model.TurnState;
import fun.javierchen.jcaiagentbackend.voice.provider.asr.AsrProvider;
import fun.javierchen.jcaiagentbackend.voice.provider.tts.TtsProvider;
import reactor.core.Disposable;

import java.time.Instant;

public class VoiceTurnContext {

    private final String sessionId;
    private final Long userId;
    private final Long tenantId;
    private final String turnId;
    private final String chatId;
    private final String messageId;
    private final Boolean webSearchEnabled;
    private final Instant createdAt = Instant.now();
    private final StringBuilder assistantBuffer = new StringBuilder();

    private volatile Instant updatedAt = createdAt;
    private volatile String transcript;
    private volatile TurnState state = TurnState.input;
    private volatile TurnEndReason endReason;
    private volatile boolean textCompleted;
    private volatile boolean audioCompleted;
    private volatile boolean outputStarted;
    private volatile Disposable llmSubscription;
    private volatile AsrProvider.AsrSession asrSession;
    private volatile TtsProvider.TtsSynthesis ttsSynthesis;

    public VoiceTurnContext(String sessionId, Long userId, Long tenantId, String turnId,
                            String chatId, String messageId, Boolean webSearchEnabled) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.turnId = turnId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.webSearchEnabled = webSearchEnabled;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getMessageId() {
        return messageId;
    }

    public Boolean getWebSearchEnabled() {
        return webSearchEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized String getTranscript() {
        return transcript;
    }

    public synchronized void setTranscript(String transcript) {
        this.transcript = transcript;
        this.updatedAt = Instant.now();
    }

    public synchronized TurnState getState() {
        return state;
    }

    public synchronized TurnEndReason getEndReason() {
        return endReason;
    }

    public synchronized void transitionTo(TurnState nextState) {
        if (state == TurnState.ended) {
            return;
        }
        this.state = nextState;
        this.updatedAt = Instant.now();
    }

    public synchronized boolean markEnded(TurnEndReason reason) {
        if (state == TurnState.ended) {
            return false;
        }
        this.state = TurnState.ended;
        this.endReason = reason;
        this.updatedAt = Instant.now();
        return true;
    }

    public synchronized boolean isEnded() {
        return state == TurnState.ended;
    }

    public synchronized void appendAssistantText(String delta) {
        this.assistantBuffer.append(delta);
        this.updatedAt = Instant.now();
    }

    public synchronized String getAssistantText() {
        return assistantBuffer.toString();
    }

    public boolean isTextCompleted() {
        return textCompleted;
    }

    public void setTextCompleted(boolean textCompleted) {
        this.textCompleted = textCompleted;
        this.updatedAt = Instant.now();
    }

    public boolean isAudioCompleted() {
        return audioCompleted;
    }

    public void setAudioCompleted(boolean audioCompleted) {
        this.audioCompleted = audioCompleted;
        this.updatedAt = Instant.now();
    }

    public boolean isOutputStarted() {
        return outputStarted;
    }

    public void setOutputStarted(boolean outputStarted) {
        this.outputStarted = outputStarted;
        this.updatedAt = Instant.now();
    }

    public Disposable getLlmSubscription() {
        return llmSubscription;
    }

    public void setLlmSubscription(Disposable llmSubscription) {
        this.llmSubscription = llmSubscription;
    }

    public AsrProvider.AsrSession getAsrSession() {
        return asrSession;
    }

    public void setAsrSession(AsrProvider.AsrSession asrSession) {
        this.asrSession = asrSession;
    }

    public TtsProvider.TtsSynthesis getTtsSynthesis() {
        return ttsSynthesis;
    }

    public void setTtsSynthesis(TtsProvider.TtsSynthesis ttsSynthesis) {
        this.ttsSynthesis = ttsSynthesis;
    }
}