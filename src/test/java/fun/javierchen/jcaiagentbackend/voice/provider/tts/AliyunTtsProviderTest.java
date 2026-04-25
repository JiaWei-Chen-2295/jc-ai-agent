package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class AliyunTtsProviderTest {

    @Test
    void synthesizeStreamSkipsWhenTextBlank() {
        AliyunTtsProvider provider = new AliyunTtsProvider(new VoiceProperties());
        TestTtsListener listener = new TestTtsListener();

        provider.synthesizeStream(turnContext(), "  ", listener);

        assertThat(listener.starts).isEqualTo(1);
        assertThat(listener.completedFlags).containsExactly(true);
        assertThat(listener.audioChunks).isEmpty();
        assertThat(listener.errors).isEmpty();
    }

    @Test
    void synthesizeStreamSkipsWhenApiKeyMissing() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getTts().setApiKey(null);
        AliyunTtsProvider provider = new AliyunTtsProvider(voiceProperties);
        TestTtsListener listener = new TestTtsListener();

        provider.synthesizeStream(turnContext(), "hello", listener);

        assertThat(listener.starts).isEqualTo(1);
        assertThat(listener.completedFlags).containsExactly(true);
        assertThat(listener.audioChunks).isEmpty();
        assertThat(listener.errors).isEmpty();
    }

    @Test
    void synthesizeStreamEmitsAudioChunksAndCompletion() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getTts().setApiKey("test-key");
        voiceProperties.getTts().setVoice("longxiaochun");
        voiceProperties.getTts().setOutputFormat("mp3");
        AliyunTtsProvider provider = new AliyunTtsProvider(voiceProperties);
        TestTtsListener listener = new TestTtsListener();

        try (MockedConstruction<SpeechSynthesizer> mocked = mockConstruction(SpeechSynthesizer.class, (mock, context) -> {
            org.mockito.Mockito.when(mock.streamCall(any())).thenReturn(Flowable.just(
                    synthesisResult(new byte[]{1, 2}),
                    synthesisResult(new byte[]{3, 4, 5})
            ));
        })) {
            provider.synthesizeStream(turnContext(), "hello", listener);

            assertThat(listener.starts).isEqualTo(1);
            assertThat(listener.audioChunks).hasSize(2);
            assertThat(listener.audioChunks.get(0)).containsExactly(1, 2);
            assertThat(listener.audioChunks.get(1)).containsExactly(3, 4, 5);
            assertThat(listener.completedFlags).containsExactly(false);
            assertThat(listener.errors).isEmpty();
            assertThat(mocked.constructed()).hasSize(1);
            verify(mocked.constructed().getFirst()).streamCall(any());
        }
    }

    @Test
    void closeCancelsStreamingSubscription() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getTts().setApiKey("test-key");
        AliyunTtsProvider provider = new AliyunTtsProvider(voiceProperties);
        TestTtsListener listener = new TestTtsListener();

        try (MockedConstruction<SpeechSynthesizer> mocked = mockConstruction(SpeechSynthesizer.class, (mock, context) -> {
            org.mockito.Mockito.when(mock.streamCall(any())).thenReturn(Flowable.never());
        })) {
            TtsProvider.TtsSynthesis synthesis = provider.synthesizeStream(turnContext(), "hello", listener);
            synthesis.close();

            assertThat(listener.starts).isEqualTo(1);
            assertThat(listener.completedFlags).isEmpty();
            assertThat(listener.audioChunks).isEmpty();
            assertThat(listener.errors).isEmpty();
            verify(mocked.constructed().getFirst()).streamCall(any());
        }
    }

    private static VoiceTurnContext turnContext() {
        return new VoiceTurnContext("ws-1", 1L, 2L, "turn-1", "chat-1", "msg-1", false);
    }

    private static SpeechSynthesisResult synthesisResult(byte[] audio) {
        SpeechSynthesisResult result = new SpeechSynthesisResult();
        result.setAudioFrame(ByteBuffer.wrap(audio));
        return result;
    }

    private static final class TestTtsListener implements TtsProvider.TtsListener {

        private int starts;
        private final List<byte[]> audioChunks = new ArrayList<>();
        private final List<Boolean> completedFlags = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onStart() {
            starts++;
        }

        @Override
        public void onAudioChunk(byte[] audioChunk) {
            audioChunks.add(audioChunk);
        }

        @Override
        public void onCompleted(boolean skipped) {
            completedFlags.add(skipped);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }
}