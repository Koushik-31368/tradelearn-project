package com.tradelearn.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.origins:http://localhost:3000}")
    private String corsOrigins;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String[] origins = parseOrigins(corsOrigins);

        // Register endpoint and allow configured origins
        // Note: SockJS will negotiate ws/wss as needed.
        registry.addEndpoint("/ws-game")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    private String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) return new String[] {"http://localhost:3000"};
        return raw.split("\\s*,\\s*");
    }
}
