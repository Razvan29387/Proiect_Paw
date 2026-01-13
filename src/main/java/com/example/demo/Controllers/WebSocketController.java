package com.example.demo.Controllers;

import com.example.demo.DTO.UserLocationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RecommandationService recommandationService;

    public WebSocketController(SimpMessagingTemplate messagingTemplate, RecommandationService recommandationService) {
        this.messagingTemplate = messagingTemplate;
        this.recommandationService = recommandationService;
    }

    /**
     * Ascultă mesajele trimise de client la destinația "/app/updateLocation".
     * Clientul trimite locația curentă (simulată prin click pe hartă).
     */
    @MessageMapping("/updateLocation")
    public void handleLocationUpdate(@Payload UserLocationDto location) {
        System.out.println("Received location update: " + location);

        // Procesăm asincron pentru a nu bloca thread-ul de WebSocket
        CompletableFuture.runAsync(() -> {
            try {
                // Cerem AI-ului o recomandare "Live" bazată pe locația exactă
                String liveRecommendation = recommandationService.getLiveRecommendation(location);
                
                // Trimitem răspunsul înapoi pe topicul public (sau privat, dar simplificăm aici)
                messagingTemplate.convertAndSend("/topic/alerts", liveRecommendation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
