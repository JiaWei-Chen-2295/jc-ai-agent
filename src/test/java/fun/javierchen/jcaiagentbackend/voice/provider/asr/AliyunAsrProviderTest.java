package fun.javierchen.jcaiagentbackend.voice.provider.asr;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.asr.recognition.timestamp.Sentence;
import com.alibaba.dashscope.common.ResultCallback;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class AliyunAsrProviderTest {

    @Test
    void startSessionReportsErrorWhenApiKeyMissing() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getAsr().setApiKey(null);
        AliyunAsrProvider provider = new AliyunAsrProvider(voiceProperties);
        TestAsrListener listener = new TestAsrListener();

        provider.startSession(turnContext(), listener);

        assertThat(listener.errors).hasSize(1);
        assertThat(listener.errors.getFirst())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DashScope API key is not configured for ASR");
        assertThat(listener.partials).isEmpty();
        assertThat(listener.finals).isEmpty();
    }

    @Test
    void startSessionStreamsPartialAndFinalTranscriptAndForwardsAudio() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getAsr().setApiKey("test-key");
        voiceProperties.getAsr().setModel("paraformer-realtime-v1");
        AliyunAsrProvider provider = new AliyunAsrProvider(voiceProperties);
        TestAsrListener listener = new TestAsrListener();

        try (MockedConstruction<Recognition> mocked = mockConstruction(Recognition.class, (mock, context) -> {
            org.mockito.Mockito.doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                ResultCallback<RecognitionResult> callback = invocation.getArgument(1);
                callback.onEvent(recognitionResult("hello", false, false));
                callback.onEvent(recognitionResult("hello world", true, false));
                callback.onComplete();
                return null;
            }).when(mock).call(any(RecognitionParam.class), org.mockito.ArgumentMatchers.<ResultCallback<RecognitionResult>>any());
        })) {
            AsrProvider.AsrSession session = provider.startSession(turnContext(), listener);
            byte[] audioChunk = new byte[]{1, 2, 3};

            session.sendAudioChunk(audioChunk);
            session.stop();

            assertThat(listener.partials).containsExactly("hello");
            assertThat(listener.finals).containsExactly("hello world");
            assertThat(listener.errors).isEmpty();
            assertThat(mocked.constructed()).hasSize(1);
            Recognition recognition = mocked.constructed().getFirst();
            verify(recognition).sendAudioFrame(any(ByteBuffer.class));
            verify(recognition).stop();
        }
    }

    @Test
    void closeSuppressesLateRecognitionErrors() {
        VoiceProperties voiceProperties = new VoiceProperties();
        voiceProperties.getAsr().setApiKey("test-key");
        AliyunAsrProvider provider = new AliyunAsrProvider(voiceProperties);
        TestAsrListener listener = new TestAsrListener();

        try (MockedConstruction<Recognition> mocked = mockConstruction(Recognition.class, (mock, context) -> {
            org.mockito.Mockito.doAnswer(invocation -> null).when(mock)
                    .call(any(RecognitionParam.class), org.mockito.ArgumentMatchers.<ResultCallback<RecognitionResult>>any());
            org.mockito.Mockito.doThrow(new RuntimeException("socket already closed")).when(mock).sendAudioFrame(any(ByteBuffer.class));
        })) {
            AsrProvider.AsrSession session = provider.startSession(turnContext(), listener);
            session.close();
            session.sendAudioChunk(new byte[]{9});

            assertThat(listener.errors).isEmpty();
            Recognition recognition = mocked.constructed().getFirst();
            verify(recognition).stop();
        }
    }

    private static VoiceTurnContext turnContext() {
        return new VoiceTurnContext("ws-1", 1L, 2L, "turn-1", "chat-1", "msg-1", false);
    }

    private static RecognitionResult recognitionResult(String text, boolean sentenceEnd, boolean completeResult) {
        RecognitionResult result = new RecognitionResult();
        Sentence sentence = new Sentence();
        sentence.setText(text);
        result.setSentence(sentence);
        result.setCompleteResult(completeResult);
        if (sentenceEnd) {
            sentence.setBeginTime(0L);
            sentence.setEndTime(1L);
        }
        return new RecognitionResult() {
            {
                setSentence(sentence);
                setCompleteResult(completeResult);
            }

            @Override
            public boolean isSentenceEnd() {
                return sentenceEnd;
            }
        };
    }

    private static final class TestAsrListener implements AsrProvider.AsrListener {

        private final List<String> partials = new ArrayList<>();
        private final List<String> finals = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onPartial(String text) {
            partials.add(text);
        }

        @Override
        public void onFinal(String text) {
            finals.add(text);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }
}