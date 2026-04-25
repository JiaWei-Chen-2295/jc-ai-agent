package fun.javierchen.jcaiagentbackend.voice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VoiceProperties.class)
public class VoiceModuleConfig {
}