package com.openclaw4.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RunEventWebSocketHandler runEventWebSocketHandler;

    public WebSocketConfig(RunEventWebSocketHandler runEventWebSocketHandler) {
        this.runEventWebSocketHandler = runEventWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(runEventWebSocketHandler, "/ws/runs/*")
                .setAllowedOrigins("*");
    }
}
