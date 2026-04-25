package fun.javierchen.jcaiagentbackend.voice.provider;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = VoiceProviderLiveApiTest.TestConfig.class)
@ActiveProfiles("local")
class VoiceProviderLiveApiTest {

    private static final String TEST_TEXT = "你好，这是语音服务联调测试。";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(90);

    @Autowired
    private VoiceProperties voiceProperties;

    @DynamicPropertySource
    static void registerVoiceProperties(DynamicPropertyRegistry registry) {
        String apiKey = firstNonBlank(
                System.getProperty("JC_AI_AGENT_API_KEY"),
                System.getenv("JC_AI_AGENT_API_KEY")
        );

        registry.add("spring.ai.dashscope.api-key", () -> defaultString(apiKey));
        registry.add("jc-ai-agent.voice.asr.api-key", () -> defaultString(firstNonBlank(
                System.getProperty("JC_VOICE_ASR_API_KEY"),
                System.getenv("JC_VOICE_ASR_API_KEY"),
                apiKey
        )));
        registry.add("jc-ai-agent.voice.tts.api-key", () -> defaultString(firstNonBlank(
                System.getProperty("JC_VOICE_TTS_API_KEY"),
                System.getenv("JC_VOICE_TTS_API_KEY"),
                apiKey
        )));
        registry.add("jc-ai-agent.voice.asr.model", () -> defaultString(firstNonBlank(
                System.getProperty("JC_VOICE_ASR_MODEL"),
                System.getenv("JC_VOICE_ASR_MODEL"),
                "paraformer-realtime-v1"
        )));
        registry.add("jc-ai-agent.voice.tts.model", () -> defaultString(firstNonBlank(
                System.getProperty("JC_VOICE_TTS_MODEL"),
                System.getenv("JC_VOICE_TTS_MODEL"),
                "cosyvoice-v1"
        )));
        registry.add("jc-ai-agent.voice.tts.voice", () -> defaultString(firstNonBlank(
                System.getProperty("JC_VOICE_TTS_VOICE"),
                System.getenv("JC_VOICE_TTS_VOICE"),
                "longxiaochun"
        )));
        registry.add("jc-ai-agent.voice.asr.format", () -> "wav");
        registry.add("jc-ai-agent.voice.asr.sample-rate", () -> "16000");
    }

    @Test
    void ttsShouldReadConfiguredApiKeyAndReturnAudio() throws Exception {
        Assumptions.assumeTrue(hasApiKeyConfigured(), "No DashScope API key configured for live voice smoke test");

        byte[] audioBytes = synthesizeWave(TEST_TEXT);

        assertThat(audioBytes).isNotEmpty();
        assertThat(voiceProperties.getTts().getApiKey()).isNotBlank();
    }

    @Test
    void asrShouldTranscribeWaveProducedByTts() throws Exception {
        Assumptions.assumeTrue(hasApiKeyConfigured(), "No DashScope API key configured for live voice smoke test");

        byte[] wavAudio = synthesizeWave(TEST_TEXT);
        File tempFile = File.createTempFile("voice-live-test-", ".wav");
        try {
            Files.write(tempFile.toPath(), wavAudio);
            RecognitionParam param = RecognitionParam.builder()
                    .apiKey(voiceProperties.getAsr().getApiKey())
                    .model(voiceProperties.getAsr().getModel())
                    .sampleRate(voiceProperties.getAsr().getSampleRate())
                    .format("wav")
                    .build();
            Recognition recognition = new Recognition();
            String transcript = recognition.call(param, tempFile);

            assertThat(transcript).isNotBlank();
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    private byte[] synthesizeWave(String text) {
        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(voiceProperties.getTts().getApiKey())
                .model(voiceProperties.getTts().getModel())
                .text(text)
                .format(SpeechSynthesisAudioFormat.WAV)
                .sampleRate(voiceProperties.getAsr().getSampleRate());
        if (hasText(voiceProperties.getTts().getVoice())) {
            builder.parameter("voice", voiceProperties.getTts().getVoice());
        }

        SpeechSynthesizer synthesizer = new SpeechSynthesizer();
        ByteBuffer buffer = synthesizer.call(builder.build());
        assertThat(buffer).isNotNull();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private boolean hasApiKeyConfigured() {
        return hasText(voiceProperties.getTts().getApiKey()) || hasText(voiceProperties.getAsr().getApiKey());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(VoiceProperties.class)
    static class TestConfig {
    }
}