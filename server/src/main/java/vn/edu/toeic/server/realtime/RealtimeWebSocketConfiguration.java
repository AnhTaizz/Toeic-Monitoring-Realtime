package vn.edu.toeic.server.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfiguration implements WebSocketConfigurer {
    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor authentication;
    public RealtimeWebSocketConfiguration(RealtimeWebSocketHandler handler, RealtimeHandshakeInterceptor authentication) {
        this.handler = handler;
        this.authentication = authentication;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/v1/realtime").addInterceptors(authentication);
    }
    @Bean
    ServletServerContainerFactoryBean websocketContainer(@Value("${toeic.ws.max-message-bytes:65536}") int maxBytes) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxBytes);
        container.setMaxBinaryMessageBufferSize(maxBytes);
        return container;
    }
}
