package fun.javierchen.jcaiagentbackend.voice.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "jc-ai-agent.voice")
public class VoiceProperties {

    private String websocketPath = "/ws/voice";

    private Duration sessionIdleTimeout = Duration.ofMinutes(30);

    private Duration reconnectGracePeriod = Duration.ofSeconds(45);

    private Duration staleEvictionInterval = Duration.ofSeconds(60);

    private Duration textStreamTimeout = Duration.ofMinutes(3);

    private String ttsAudioMimeType = "audio/mpeg";

    private String codec = "audio/mpeg";

    private final AsrProperties asr = new AsrProperties();

    private final TtsProperties tts = new TtsProperties();

    public String resolveTtsAudioMimeType() {
        String configuredFormat = StringUtils.upperCase(StringUtils.trimToEmpty(tts.getOutputFormat()));
        if (configuredFormat.startsWith("PCM")) {
            return "audio/pcm";
        }
        if (configuredFormat.startsWith("WAV")) {
            return "audio/wav";
        }
        return StringUtils.defaultIfBlank(ttsAudioMimeType, "audio/mpeg");
    }

    @Data
    public static class AsrProperties {
        private String provider = "aliyun";
        private String apiKey;
        private String model = "paraformer-realtime-v1";
        private String appKey;
        private String accessKeyId;
        private String accessKeySecret;
        private int sampleRate = 16000;
        private String format = "pcm";
    }

    @Data
    public static class TtsProperties {
        private String provider = "aliyun";
        private String baseUrl;
        private String apiKey;
        private String voice = "longxiaochun";
        private String model = "cosyvoice-v1";
        private String outputFormat = "mp3";
        private Float speechRate;
        private Float pitchRate;
        private Integer volume;
    }
}