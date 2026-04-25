package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import io.reactivex.disposables.Disposable;
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
    public TtsSynthesis synthesizeStream(VoiceTurnContext turnContext, String text, TtsListener listener) {
        if (StringUtils.isBlank(text)) {
            listener.onCompleted(true);
            return NoopTtsSynthesis.INSTANCE;
        }
        String apiKey = resolveApiKey();
        if (StringUtils.isBlank(apiKey)) {
            log.info("Skipping TTS streaming because provider credentials are not configured: turnId={}", turnContext.getTurnId());
            listener.onCompleted(true);
            return NoopTtsSynthesis.INSTANCE;
        }

        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(resolveModel())
                .text(text)
                .format(resolveAudioFormat())
                .sampleRate(16000);
        String voice = StringUtils.trimToNull(voiceProperties.getTts().getVoice());
        if (voice != null) {
            builder.parameter("voice", voice);
        }

        SpeechSynthesisParam param = builder.build();
        SpeechSynthesizer synthesizer = new SpeechSynthesizer();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger emittedChunkCount = new AtomicInteger(0);
        try {
            Disposable subscription = synthesizer.streamCall(param).subscribe(
                    result -> emitAudioChunk(result, listener, closed, started, emittedChunkCount),
                    error -> {
                        if (!closed.get()) {
                            listener.onError(error);
                        }
                    },
                    () -> {
                        if (!closed.get()) {
                            boolean skipped = emittedChunkCount.get() == 0;
                            if (skipped) {
                                skipped = !emitBufferedAudioData(synthesizer, listener, started, emittedChunkCount);
                            }
                            if (skipped) {
                                log.warn("Aliyun TTS stream completed without audio frames: turnId={}, model={}, voice={}",
                                        turnContext.getTurnId(), param.getModel(), voice);
                            }
                            listener.onCompleted(skipped);
                        }
                    }
            );
            log.info("Started Aliyun TTS stream: turnId={}, model={}, voice={}, format={}",
                    turnContext.getTurnId(), param.getModel(), voice, param.getFormat().getValue());
            return new StreamingTtsSynthesis(subscription, closed);
        } catch (Exception e) {
            log.error("Failed to start Aliyun TTS stream: turnId={}", turnContext.getTurnId(), e);
            listener.onError(e);
            return NoopTtsSynthesis.INSTANCE;
        }
    }

    private String resolveApiKey() {
        return StringUtils.firstNonBlank(
                voiceProperties.getTts().getApiKey(),
                defaultDashScopeApiKey
        );
    }

    private String resolveModel() {
        return StringUtils.defaultIfBlank(voiceProperties.getTts().getModel(), "cosyvoice-v1");
    }

    private SpeechSynthesisAudioFormat resolveAudioFormat() {
        String configuredFormat = StringUtils.upperCase(StringUtils.defaultIfBlank(voiceProperties.getTts().getOutputFormat(), "mp3"));
        return switch (configuredFormat) {
            case "PCM" -> SpeechSynthesisAudioFormat.PCM;
            case "WAV" -> SpeechSynthesisAudioFormat.WAV;
            default -> SpeechSynthesisAudioFormat.MP3;
        };
    }

    private void emitAudioChunk(SpeechSynthesisResult result,
                                TtsListener listener,
                                AtomicBoolean closed,
                                AtomicBoolean started,
                                AtomicInteger emittedChunkCount) {
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
        emittedChunkCount.incrementAndGet();
        listener.onAudioChunk(chunk);
    }

    private boolean emitBufferedAudioData(SpeechSynthesizer synthesizer,
                                          TtsListener listener,
                                          AtomicBoolean started,
                                          AtomicInteger emittedChunkCount) {
        ByteBuffer audioData = synthesizer.getAudioData();
        if (audioData == null) {
            return false;
        }
        ByteBuffer buffer = audioData.asReadOnlyBuffer();
        if (!buffer.hasRemaining()) {
            return false;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        if (started.compareAndSet(false, true)) {
            listener.onStart();
        }
        emittedChunkCount.incrementAndGet();
        listener.onAudioChunk(chunk);
        log.info("Aliyun TTS used buffered audio fallback: frameBytes={}", chunk.length);
        return true;
    }

    private static final class StreamingTtsSynthesis implements TtsSynthesis {

        private final Disposable subscription;
        private final AtomicBoolean closed;

        private StreamingTtsSynthesis(Disposable subscription, AtomicBoolean closed) {
            this.subscription = subscription;
            this.closed = closed;
        }

        @Override
        public void cancel() {
            close();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true) && subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        }
    }

    private static final class NoopTtsSynthesis implements TtsSynthesis {

        private static final NoopTtsSynthesis INSTANCE = new NoopTtsSynthesis();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void cancel() {
            closed.set(true);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}