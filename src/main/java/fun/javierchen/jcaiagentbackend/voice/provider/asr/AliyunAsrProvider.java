package fun.javierchen.jcaiagentbackend.voice.provider.asr;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunAsrProvider implements AsrProvider {

    private final VoiceProperties voiceProperties;
    @Value("${spring.ai.dashscope.api-key:}")
    private String defaultDashScopeApiKey;

    @Override
    public AsrSession startSession(VoiceTurnContext turnContext, AsrListener listener) {
        String apiKey = resolveApiKey();
        if (StringUtils.isBlank(apiKey)) {
            log.warn("Skipping Aliyun ASR because no DashScope API key is configured: turnId={}", turnContext.getTurnId());
            return new FailedAliyunAsrSession(listener, "DashScope API key is not configured for ASR");
        }

        RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model(resolveModel())
                .sampleRate(voiceProperties.getAsr().getSampleRate())
                .format(StringUtils.defaultIfBlank(voiceProperties.getAsr().getFormat(), "pcm"))
                .build();

        Recognition recognition = new Recognition();
        RealAliyunAsrSession session = new RealAliyunAsrSession(recognition, listener);
        try {
            recognition.call(param, new ResultCallback<>() {
                @Override
                public void onEvent(RecognitionResult result) {
                    session.handleRecognitionEvent(result);
                }

                @Override
                public void onComplete() {
                    session.handleComplete();
                }

                @Override
                public void onError(Exception e) {
                    session.handleError(e);
                }
            });
            log.info("Started Aliyun ASR stream: turnId={}, model={}, sampleRate={}, format={}",
                    turnContext.getTurnId(), param.getModel(), param.getSampleRate(), param.getFormat());
            return session;
        } catch (Exception e) {
            log.error("Failed to start Aliyun ASR stream: turnId={}", turnContext.getTurnId(), e);
            return new FailedAliyunAsrSession(listener, e);
        }
    }

    private String resolveApiKey() {
        return StringUtils.firstNonBlank(
                voiceProperties.getAsr().getApiKey(),
                defaultDashScopeApiKey
        );
    }

    private String resolveModel() {
        return StringUtils.defaultIfBlank(voiceProperties.getAsr().getModel(), "paraformer-realtime-v1");
    }

    private static final class RealAliyunAsrSession implements AsrSession {

        private final Recognition recognition;
        private final AsrListener listener;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean finalDelivered = new AtomicBoolean(false);
        private final AtomicReference<String> lastTranscript = new AtomicReference<>();

        private RealAliyunAsrSession(Recognition recognition, AsrListener listener) {
            this.recognition = recognition;
            this.listener = listener;
        }

        @Override
        public void sendAudioChunk(byte[] audioChunk) {
            if (closed.get() || audioChunk == null || audioChunk.length == 0) {
                return;
            }
            try {
                recognition.sendAudioFrame(ByteBuffer.wrap(audioChunk));
            } catch (Exception e) {
                handleError(e);
            }
        }

        @Override
        public void stop() {
            if (closed.compareAndSet(false, true)) {
                recognition.stop();
            }
        }

        @Override
        public void close() {
            stop();
        }

        private void handleRecognitionEvent(RecognitionResult result) {
            if (result == null) {
                return;
            }
            String text = result.getSentence() == null ? null : StringUtils.trimToNull(result.getSentence().getText());
            if (text != null) {
                lastTranscript.set(text);
            }
            if (text == null || finalDelivered.get()) {
                return;
            }
            if (result.isSentenceEnd() || result.isCompleteResult()) {
                if (finalDelivered.compareAndSet(false, true)) {
                    listener.onFinal(text);
                }
                return;
            }
            listener.onPartial(text);
        }

        private void handleComplete() {
            if (finalDelivered.get()) {
                return;
            }
            String transcript = StringUtils.trimToNull(lastTranscript.get());
            if (transcript != null && finalDelivered.compareAndSet(false, true)) {
                listener.onFinal(transcript);
            }
        }

        private void handleError(Throwable error) {
            if (closed.get()) {
                return;
            }
            listener.onError(error);
        }
    }

    private static final class FailedAliyunAsrSession implements AsrSession {

        private final AtomicBoolean closed = new AtomicBoolean(false);

        private FailedAliyunAsrSession(AsrListener listener, String message) {
            listener.onError(new IllegalStateException(message));
        }

        private FailedAliyunAsrSession(AsrListener listener, Throwable error) {
            listener.onError(error);
        }

        @Override
        public void sendAudioChunk(byte[] audioChunk) {
        }

        @Override
        public void stop() {
            closed.set(true);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}