package com.tradelearn.server.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.tradelearn.server.security.WebSocketAuthInterceptor;
import com.tradelearn.server.security.WebSocketChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,https://tradelearn-project.vercel.app,https://tradelearn-project-kethans-projects-3fb29448.vercel.app}")
    private String allowedOrigins;

    private final WebSocketAuthInterceptor wsAuthInterceptor;
    private final WebSocketChannelInterceptor wsChannelInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor wsAuthInterceptor,
                           WebSocketChannelInterceptor wsChannelInterceptor) {
        this.wsAuthInterceptor = wsAuthInterceptor;
        this.wsChannelInterceptor = wsChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(
            @NonNull StompEndpointRegistry registry) {

        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .addInterceptors(wsAuthInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(
            @NonNull MessageBrokerRegistry registry) {

        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(
            @NonNull ChannelRegistration registration) {
        registration.interceptors(wsChannelInterceptor);

        // Size the inbound channel executor for 10K concurrent games.
        // Each trade handler now submits to the TradeProcessingPipeline,
        // so inbound threads just do auth + rate-limit + submit (~100μs).
        // 128 threads can handle 1M+ submissions/sec.
        registration.taskExecutor()
            .corePoolSize(4)
            .maxPoolSize(8)
            .queueCapacity(20);
    }

    /**
     * Outbound channel: bounded queue for messages going TO clients.
     * Prevents unbounded buffering if clients are slow to consume.
     */
    @Override
    public void configureClientOutboundChannel(
            @NonNull ChannelRegistration registration) {
        registration.taskExecutor()
            .corePoolSize(4)
            .maxPoolSize(8)
            .queueCapacity(20);
    }

    /**
     * WebSocket transport limits for slow consumer detection:
     * <ul>
     *   <li><b>messageSizeLimit</b>: Max inbound STOMP frame (16 KB) — prevents oversized payloads.</li>
     *   <li><b>sendBufferSizeLimit</b>: Max outbound buffer per session (1 MB) — when exceeded,
     *       the session is closed with {@code SESSION_NOT_RELIABLE} (1011). This IS the slow
     *       consumer detection mechanism.</li>
     *   <li><b>sendTimeLimit</b>: Max time to send a single message (15 s) — sessions that
     *       can't receive within this window are force-closed.</li>
     *   <li><b>timeToFirstMessage</b>: Max time from connect to first message (30 s) —
     *       prevents idle connection hoarding.</li>
     * </ul>
     */
    @Override
    public void configureWebSocketTransport(
            @NonNull WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024);            // 16 KB max inbound
        registration.setSendBufferSizeLimit(1024 * 1024);       // 1 MB per session outbound
        registration.setSendTimeLimit(15 * 1000);               // 15 s send timeout
        registration.setTimeToFirstMessage(30 * 1000);          // 30 s to first message
    }
}