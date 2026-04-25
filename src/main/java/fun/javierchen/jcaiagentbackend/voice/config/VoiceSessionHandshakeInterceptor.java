package fun.javierchen.jcaiagentbackend.voice.config;

import fun.javierchen.jcaiagentbackend.constant.UserConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
@Component
public class VoiceSessionHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_HTTP_SESSION_ID = "voice.httpSessionId";
    public static final String ATTR_USER_ID = "voice.userId";
    public static final String ATTR_TENANT_ID = "voice.tenantId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)) {
            return false;
        }

        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        HttpSession session = httpServletRequest.getSession(false);
        if (session == null) {
            servletResponse.getServletResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        Long userId = parseLong(session.getAttribute(UserConstant.USER_LOGIN_STATE));
        if (userId == null) {
            servletResponse.getServletResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        Long tenantId = parseLong(session.getAttribute(UserConstant.USER_ACTIVE_TENANT_ID));
        attributes.put(ATTR_HTTP_SESSION_ID, session.getId());
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_TENANT_ID, tenantId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("Voice handshake completed with exception", exception);
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}