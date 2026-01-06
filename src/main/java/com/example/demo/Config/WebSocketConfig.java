package com.example.demo.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefixul pentru mesajele care merg de la server la client (Push)
        config.enableSimpleBroker("/topic");
        // Prefixul pentru mesajele care vin de la client la server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Punctul de conectare pentru frontend
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Permite conexiuni de la React (localhost:5173)
                .withSockJS(); // Activează fallback pentru browsere vechi
    }
}
