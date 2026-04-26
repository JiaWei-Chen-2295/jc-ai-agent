package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunTtsProvider implements TtsProvider {

    private final VoiceProperties voiceProperties;

    @Value("${spring.ai.dashscope.api-key:}")
    private String defaultDashScopeApiKey;

    @Override
    public TtsSynthesis openStream(VoiceTurnContext turnContext, TtsListener listener) {
        String apiKey = resolveApiKey();
        if (StringUtils.isBlank(apiKey)) {
            log.info("Skipping TTS streaming because provider credentials are not configured: turnId={}", turnContext.getTurnId());
            listener.onCompleted(true);
            return NoopTtsSynthesis.INSTANCE;
        }

        SpeechSynthesisAudioFormat format = resolveAudioFormat();
        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(resolveModel())
                .format(format);
        String voice = StringUtils.trimToNull(voiceProperties.getTts().getVoice());
        if (voice != null) {
            builder.voice(voice);
        }
        if (voiceProperties.getTts().getSpeechRate() != null) {
            builder.speechRate(voiceProperties.getTts().getSpeechRate());
        }
        if (voiceProperties.getTts().getPitchRate() != null) {
            builder.pitchRate(voiceProperties.getTts().getPitchRate());
        }
        if (voiceProperties.getTts().getVolume() != null) {
            builder.volume(voiceProperties.getTts().getVolume());
        }

        SpeechSynthesisParam param = builder.build();
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger emittedChunks = new AtomicInteger(0);
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (closed.get() || result == null || result.getAudioFrame() == null) {
                    return;
                }
                ByteBuffer buffer = result.getAudioFrame().asReadOnlyBuffer();
                if (!buffer.hasRemaining()) {
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                if (started.compareAndSet(false, true)) {
                    listener.onStart();
                }
                emittedChunks.incrementAndGet();
                listener.onAudioChunk(chunk);
            }

            @Override
            public void onComplete() {
                if (closed.get()) {
                    return;
                }
                completed.set(true);
                listener.onCompleted(emittedChunks.get() == 0);
            }

            @Override
            public void onError(Exception e) {
                if (!closed.get()) {
                    listener.onError(e);
                }
            }
        };

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);
        log.info("Started Aliyun TTS bidirectional stream: turnId={}, model={}, voice={}, format={}",
                turnContext.getTurnId(), param.getModel(), voice, format.name());
        return new BidiTtsSynthesis(synthesizer, listener, closed, completed, emittedChunks);
    }

    private String resolveApiKey() {
        return StringUtils.firstNonBlank(voiceProperties.getTts().getApiKey(), defaultDashScopeApiKey);
    }

    private String resolveModel() {
        return StringUtils.defaultIfBlank(voiceProperties.getTts().getModel(), "cosyvoice-v1");
    }

    private SpeechSynthesisAudioFormat resolveAudioFormat() {
        String configuredFormat = StringUtils.upperCase(StringUtils.trimToEmpty(voiceProperties.getTts().getOutputFormat()));
        if (configuredFormat.isEmpty() || "MP3".equals(configuredFormat)) {
            return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
        }
        if ("PCM".equals(configuredFormat)) {
            return SpeechSynthesisAudioFormat.PCM_22050HZ_MONO_16BIT;
        }
        if ("WAV".equals(configuredFormat)) {
            return SpeechSynthesisAudioFormat.WAV_22050HZ_MONO_16BIT;
        }
        try {
            return SpeechSynthesisAudioFormat.valueOf(configuredFormat);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown TTS outputFormat '{}', falling back to MP3_22050HZ_MONO_256KBPS", configuredFormat);
            return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
        }
    }

    private static final class BidiTtsSynthesis implements TtsSynthesis {

        private final SpeechSynthesizer synthesizer;
        private final TtsListener listener;
        private final AtomicBoolean closed;
        private final AtomicBoolean completed;
        private final AtomicInteger emittedChunks;

        private BidiTtsSynthesis(SpeechSynthesizer synthesizer, TtsListener listener,
                                 AtomicBoolean closed, AtomicBoolean completed, AtomicInteger emittedChunks) {
            this.synthesizer = synthesizer;
            this.listener = listener;
            this.closed = closed;
            this.completed = completed;
            this.emittedChunks = emittedChunks;
        }

        @Override
        public synchronized void appendText(String textDelta) {
            if (closed.get() || StringUtils.isBlank(textDelta)) {
                return;
            }
            try {
                synthesizer.streamingCall(textDelta);
            } catch (Exception e) {
                if (!closed.get()) {
                    listener.onError(e);
                }
            }
        }

        @Override
        public synchronized void complete() {
            if (closed.get() || completed.get()) {
                return;
            }
            try {
                synthesizer.streamingComplete();
            } catch (Exception e) {
                if (!closed.get()) {
                    listener.onError(e);
                }
            } finally {
                releaseDuplex();
            }
        }

        @Override
        public void cancel() {
            close();
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (!completed.get()) {
                listener.onCompleted(emittedChunks.get() == 0);
            }
            releaseDuplex();
        }

        private void releaseDuplex() {
            try {
                if (synthesizer.getDuplexApi() != null) {
                    synthesizer.getDuplexApi().close(1000, "bye");
                }
            } catch (Exception ignored) {
                // ignore close errors to keep close path idempotent
            }
        }
    }

    private static final class NoopTtsSynthesis implements TtsSynthesis {

        private static final NoopTtsSynthesis INSTANCE = new NoopTtsSynthesis();

        @Override
        public void appendText(String textDelta) {
            // no-op
        }

        @Override
        public void complete() {
            // no-op
        }

        @Override
        public void cancel() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}