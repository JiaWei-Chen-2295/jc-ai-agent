package fun.javierchen.jcaiagentbackend.voice.service;

import fun.javierchen.jcaiagentbackend.app.StudyFriend;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.service.StudyFriendChatService;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.model.AsrTextPayload;
import fun.javierchen.jcaiagentbackend.voice.model.ErrorPayload;
import fun.javierchen.jcaiagentbackend.voice.model.SessionStatePayload;
import fun.javierchen.jcaiagentbackend.voice.model.TextDeltaPayload;
import fun.javierchen.jcaiagentbackend.voice.model.TextEndPayload;
import fun.javierchen.jcaiagentbackend.voice.model.TtsStatePayload;
import fun.javierchen.jcaiagentbackend.voice.model.TurnEndReason;
import fun.javierchen.jcaiagentbackend.voice.model.TurnState;
import fun.javierchen.jcaiagentbackend.voice.model.TurnStatePayload;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceEventEnvelope;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceSessionState;
import fun.javierchen.jcaiagentbackend.voice.model.VoiceTurnStartCommand;
import fun.javierchen.jcaiagentbackend.voice.provider.asr.AsrProvider;
import fun.javierchen.jcaiagentbackend.voice.provider.tts.TtsProvider;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceSessionContext;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceSessionRegistry;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTurnOrchestrator {

    private final StudyFriend studyFriend;
    private final StudyFriendChatService studyFriendChatService;
    private final VoiceSessionRegistry voiceSessionRegistry;
    private final VoiceTextStreamService voiceTextStreamService;
    private final AsrProvider asrProvider;
    private final TtsProvider ttsProvider;
    private final VoiceProperties voiceProperties;

    public VoiceTurnContext startTurn(VoiceSessionContext sessionContext, VoiceTurnStartCommand command) {
        if (StringUtils.isBlank(command.chatId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "chatId is required for a voice turn");
        }
        interruptActiveTurn(sessionContext, "Replaced by a newer turn");

        String turnId = StringUtils.defaultIfBlank(command.turnId(), UUID.randomUUID().toString());
        VoiceTurnContext turnContext = new VoiceTurnContext(
                sessionContext.getWebsocketSessionId(),
                sessionContext.getUserId(),
                sessionContext.getTenantId(),
                turnId,
                command.chatId(),
                command.messageId(),
                command.webSearchEnabled()
        );
        voiceSessionRegistry.registerTurn(sessionContext, turnContext);
        voiceTextStreamService.registerTurn(sessionContext.getWebsocketSessionId(), sessionContext.getUserId(), command.chatId(), turnId);
        publishTurnState(sessionContext, turnContext, TurnState.input, null);

        if (StringUtils.isNotBlank(command.transcript())) {
            commitTranscript(sessionContext, turnId, command.transcript());
        }
        return turnContext;
    }

    public void handleAudioChunk(VoiceSessionContext sessionContext, byte[] audioChunk) {
        String activeTurnId = sessionContext.getActiveTurnId();
        if (StringUtils.isBlank(activeTurnId)) {
            log.debug("Ignoring voice audio chunk because no active turn: sessionId={}", sessionContext.getWebsocketSessionId());
            return;
        }
        VoiceTurnContext turnContext = voiceSessionRegistry.findTurn(activeTurnId);
        if (turnContext == null || turnContext.isEnded()) {
            log.debug("Ignoring voice audio chunk because turn is unavailable: sessionId={}, turnId={}",
                    sessionContext.getWebsocketSessionId(), activeTurnId);
            return;
        }
        AsrProvider.AsrSession asrSession = turnContext.getAsrSession();
        if (asrSession == null) {
            synchronized (turnContext) {
                asrSession = turnContext.getAsrSession();
                if (asrSession == null) {
                    openAsrSession(sessionContext, turnContext);
                    asrSession = turnContext.getAsrSession();
                }
            }
        }
        if (asrSession == null) {
            emitError(sessionContext, turnContext.getTurnId(), "ASR_NOT_READY", "ASR session has not been started", true);
            return;
        }
        asrSession.sendAudioChunk(audioChunk);
    }

    public void stopTurn(VoiceSessionContext sessionContext, String turnId) {
        VoiceTurnContext turnContext = resolveTurn(sessionContext, turnId);
        if (turnContext.getAsrSession() != null) {
            turnContext.getAsrSession().stop();
            return;
        }
        if (StringUtils.isBlank(turnContext.getTranscript())) {
            interruptTurn(sessionContext, turnContext.getTurnId(), "Stopped without any captured transcript");
        }
    }

    public void commitTranscript(VoiceSessionContext sessionContext, String turnId, String transcript) {
        VoiceTurnContext turnContext = resolveTurn(sessionContext, turnId);
        if (turnContext.isEnded()) {
            return;
        }
        turnContext.setTranscript(transcript);
        if (turnContext.getAsrSession() != null) {
            turnContext.getAsrSession().close();
            turnContext.setAsrSession(null);
        }
        voiceSessionRegistry.sendEvent(sessionContext, envelope(sessionContext, turnId, "asr_text", new AsrTextPayload("final", transcript)));
        publishTurnState(sessionContext, turnContext, TurnState.processing, null);
        executeChatTurn(sessionContext, turnContext);
    }

    public void interruptTurn(VoiceSessionContext sessionContext, String turnId, String reason) {
        VoiceTurnContext turnContext = resolveTurn(sessionContext, turnId);
        if (!turnContext.markEnded(TurnEndReason.interrupted)) {
            return;
        }
        cleanupTurnResources(turnContext);
        voiceTextStreamService.publish(turnContext.getTurnId(), "text_end", new TextEndPayload("interrupted"));
        publishTurnState(sessionContext, turnContext, TurnState.ended, TurnEndReason.interrupted);
        log.info("Voice turn interrupted: turnId={}, reason={}", turnContext.getTurnId(), reason);
        voiceSessionRegistry.clearTurn(turnContext);
    }

    public void handleSocketClosed(VoiceSessionContext sessionContext, String reason) {
        String activeTurnId = sessionContext.getActiveTurnId();
        if (StringUtils.isBlank(activeTurnId)) {
            return;
        }
        VoiceTurnContext turnContext = voiceSessionRegistry.findTurn(activeTurnId);
        if (turnContext == null || turnContext.isEnded()) {
            return;
        }
        failTurn(sessionContext, turnContext, "VOICE_SOCKET_CLOSED", reason, true);
    }

    public void emitSessionReady(VoiceSessionContext sessionContext) {
        voiceSessionRegistry.sendEvent(sessionContext,
                envelope(sessionContext, null, "session_state", new SessionStatePayload(VoiceSessionState.ready.name())));
    }

    private void executeChatTurn(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
        try {
            var chatSession = studyFriendChatService.requireSessionForUser(
                    turnContext.getChatId(), turnContext.getTenantId(), turnContext.getUserId());
            studyFriendChatService.appendUserMessage(
                    turnContext.getChatId(),
                    turnContext.getTenantId(),
                    turnContext.getUserId(),
                    turnContext.getTranscript(),
                    turnContext.getMessageId()
            );
            boolean effectiveWebSearchEnabled = studyFriend.resolveWebSearchEnabled(turnContext.getWebSearchEnabled());
            var streamResult = studyFriend.doChatWithRAGStream(
                    turnContext.getTranscript(),
                    turnContext.getChatId(),
                    turnContext.getTenantId(),
                    chatSession.getModelId(),
                    effectiveWebSearchEnabled
            );
            TtsProvider.TtsSynthesis synthesis = ttsProvider.openStream(turnContext, buildTtsListener(sessionContext, turnContext));
            turnContext.setTtsSynthesis(synthesis);
            Disposable disposable = streamResult.contentStream().subscribe(
                    chunk -> onTextDelta(sessionContext, turnContext, chunk),
                    error -> {
                        synthesis.cancel();
                        failTurn(sessionContext, turnContext, "VOICE_TEXT_STREAM_FAILED", safeMessage(error), false);
                    },
                    () -> onTextCompleted(sessionContext, turnContext, streamResult.webSearchUsed(), streamResult.sources())
            );
            turnContext.setLlmSubscription(disposable);
        } catch (Exception e) {
            failTurn(sessionContext, turnContext, "VOICE_TURN_START_FAILED", safeMessage(e), false);
        }
    }

    private void onTextDelta(VoiceSessionContext sessionContext, VoiceTurnContext turnContext, String chunk) {
        if (turnContext.isEnded() || StringUtils.isBlank(chunk)) {
            return;
        }
        if (!turnContext.isOutputStarted()) {
            turnContext.setOutputStarted(true);
            publishTurnState(sessionContext, turnContext, TurnState.output, null);
        }
        turnContext.appendAssistantText(chunk);
        voiceTextStreamService.publish(turnContext.getTurnId(), "text_delta", new TextDeltaPayload(chunk));
        TtsProvider.TtsSynthesis synthesis = turnContext.getTtsSynthesis();
        if (synthesis != null) {
            synthesis.appendText(chunk);
        }
    }

    private void onTextCompleted(VoiceSessionContext sessionContext, VoiceTurnContext turnContext,
                                 Boolean webSearchUsed, java.util.List<fun.javierchen.jcaiagentbackend.app.StudyFriendSource> sources) {
        if (turnContext.isEnded()) {
            return;
        }
        turnContext.setTextCompleted(true);
        studyFriendChatService.appendAssistantMessage(
                turnContext.getChatId(),
                turnContext.getTenantId(),
                turnContext.getUserId(),
                turnContext.getAssistantText(),
                webSearchUsed,
                sources
        );
        voiceTextStreamService.publish(turnContext.getTurnId(), "text_end", new TextEndPayload("completed"));
        TtsProvider.TtsSynthesis synthesis = turnContext.getTtsSynthesis();
        if (synthesis != null) {
            synthesis.complete();
        }
    }

    private TtsProvider.TtsListener buildTtsListener(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
        return new TtsProvider.TtsListener() {
            @Override
            public void onStart() {
                if (!turnContext.isOutputStarted()) {
                    turnContext.setOutputStarted(true);
                    publishTurnState(sessionContext, turnContext, TurnState.output, null);
                }
                voiceSessionRegistry.sendEvent(sessionContext,
                        envelope(sessionContext, turnContext.getTurnId(), "tts_state",
                                new TtsStatePayload("start", voiceProperties.resolveTtsAudioMimeType(), false)));
            }

            @Override
            public void onAudioChunk(byte[] audioChunk) {
                voiceSessionRegistry.sendBinary(sessionContext, audioChunk);
            }

            @Override
            public void onCompleted(boolean skipped) {
                turnContext.setAudioCompleted(true);
                voiceSessionRegistry.sendEvent(sessionContext,
                        envelope(sessionContext, turnContext.getTurnId(), "tts_state",
                                new TtsStatePayload("end", voiceProperties.resolveTtsAudioMimeType(), skipped)));
                completeTurn(sessionContext, turnContext);
            }

            @Override
            public void onError(Throwable error) {
                failTurn(sessionContext, turnContext, "VOICE_TTS_FAILED", safeMessage(error), false);
            }
        };
    }

    private void completeTurn(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
        if (!turnContext.markEnded(TurnEndReason.completed)) {
            return;
        }
        publishTurnState(sessionContext, turnContext, TurnState.ended, TurnEndReason.completed);
        cleanupTurnResources(turnContext);
        voiceSessionRegistry.clearTurn(turnContext);
    }

    private void failTurn(VoiceSessionContext sessionContext, VoiceTurnContext turnContext,
                          String code, String message, boolean recoverable) {
        if (!turnContext.markEnded(TurnEndReason.failed)) {
            return;
        }
        cleanupTurnResources(turnContext);
        emitError(sessionContext, turnContext.getTurnId(), code, message, recoverable);
        voiceTextStreamService.publish(turnContext.getTurnId(), "error", new ErrorPayload(code, message, recoverable));
        publishTurnState(sessionContext, turnContext, TurnState.ended, TurnEndReason.failed);
        voiceSessionRegistry.clearTurn(turnContext);
    }

    private void publishTurnState(VoiceSessionContext sessionContext, VoiceTurnContext turnContext,
                                  TurnState state, TurnEndReason endReason) {
        turnContext.transitionTo(state);
        TurnStatePayload payload = new TurnStatePayload(turnContext.getChatId(), state, endReason);
        voiceSessionRegistry.sendEvent(sessionContext, envelope(sessionContext, turnContext.getTurnId(), "turn_state", payload));
        voiceTextStreamService.publish(turnContext.getTurnId(), "turn_state", payload);
    }

    private void openAsrSession(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
        AsrProvider.AsrSession asrSession = asrProvider.startSession(turnContext, new AsrProvider.AsrListener() {
            @Override
            public void onPartial(String text) {
                if (turnContext.isEnded()) {
                    return;
                }
                voiceSessionRegistry.sendEvent(sessionContext,
                        envelope(sessionContext, turnContext.getTurnId(), "asr_text", new AsrTextPayload("partial", text)));
            }

            @Override
            public void onFinal(String text) {
                commitTranscript(sessionContext, turnContext.getTurnId(), text);
            }

            @Override
            public void onError(Throwable error) {
                failTurn(sessionContext, turnContext, "VOICE_ASR_FAILED", safeMessage(error), true);
            }
        });
        turnContext.setAsrSession(asrSession);
    }

    private VoiceTurnContext requireActiveTurn(VoiceSessionContext sessionContext) {
        String activeTurnId = sessionContext.getActiveTurnId();
        if (StringUtils.isBlank(activeTurnId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "No active voice turn");
        }
        VoiceTurnContext turnContext = voiceSessionRegistry.findTurn(activeTurnId);
        if (turnContext == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Voice turn not found");
        }
        return turnContext;
    }

    private VoiceTurnContext resolveTurn(VoiceSessionContext sessionContext, String turnId) {
        String effectiveTurnId = StringUtils.defaultIfBlank(turnId, sessionContext.getActiveTurnId());
        if (StringUtils.isBlank(effectiveTurnId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "turnId is required");
        }
        VoiceTurnContext turnContext = voiceSessionRegistry.findTurn(effectiveTurnId);
        if (turnContext == null || !turnContext.getUserId().equals(sessionContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Voice turn not found");
        }
        return turnContext;
    }

    private void interruptActiveTurn(VoiceSessionContext sessionContext, String reason) {
        String activeTurnId = sessionContext.getActiveTurnId();
        if (StringUtils.isBlank(activeTurnId)) {
            return;
        }
        VoiceTurnContext turnContext = voiceSessionRegistry.findTurn(activeTurnId);
        if (turnContext == null || turnContext.isEnded()) {
            return;
        }
        interruptTurn(sessionContext, activeTurnId, reason);
    }

    private void cleanupTurnResources(VoiceTurnContext turnContext) {
        Disposable llmSubscription = turnContext.getLlmSubscription();
        if (llmSubscription != null) {
            llmSubscription.dispose();
        }
        if (turnContext.getAsrSession() != null) {
            turnContext.getAsrSession().close();
            turnContext.setAsrSession(null);
        }
        if (turnContext.getTtsSynthesis() != null) {
            turnContext.getTtsSynthesis().cancel();
            turnContext.setTtsSynthesis(null);
        }
    }

    private void emitError(VoiceSessionContext sessionContext, String turnId, String code, String message, boolean recoverable) {
        voiceSessionRegistry.sendEvent(sessionContext,
                envelope(sessionContext, turnId, "error", new ErrorPayload(code, message, recoverable)));
    }

    private VoiceEventEnvelope envelope(VoiceSessionContext sessionContext, String turnId, String type, Object payload) {
        return new VoiceEventEnvelope(sessionContext.getWebsocketSessionId(), turnId, Instant.now(), type, payload);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        return StringUtils.defaultIfBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
    }
}