package fun.javierchen.jcaiagentbackend.voice.config;

import fun.javierchen.jcaiagentbackend.voice.ws.VoiceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceWebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceWebSocketHandler;
    private final VoiceSessionHandshakeInterceptor handshakeInterceptor;
    private final VoiceProperties voiceProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceWebSocketHandler, voiceProperties.getWebsocketPath())
                .addInterceptors(new HttpSessionHandshakeInterceptor(), handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}