package com.example.demo.Controllers;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/Recommandations")
public class RecommandationController {

    private final RecommandationService recommandationService;
    private final SimpMessagingTemplate messagingTemplate;

    public RecommandationController(RecommandationService recommandationService, SimpMessagingTemplate messagingTemplate) {
        this.recommandationService = recommandationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Endpoint-ul principal: Obține recomandările pentru un oraș specificat.
     * Dacă orașul nu există în baza de date, serviciul îl va adăuga.
     * Ex: GET /api/v1/Recommandations/Bucuresti
     */
    @GetMapping("/{cityName}")
    public ResponseEntity<List<RecommandationDto>> getRecommandationsForCity(
            @PathVariable("cityName") String city) {
        
        // 1. Obținem recomandările standard (blocant, pentru a returna răspunsul HTTP)
        List<RecommandationDto> recommandations = recommandationService.getRecommandations(city);

        // 2. Trimitem o notificare "Push" prin WebSocket ASINCRON
        // Folosim CompletableFuture pentru a nu bloca răspunsul HTTP cât timp AI-ul generează notificarea
        CompletableFuture.runAsync(() -> {
            try {
                String alertMessage = recommandationService.getCityNotification(city);
                messagingTemplate.convertAndSend("/topic/alerts", alertMessage);
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket notification: " + e.getMessage());
            }
        });

        return ResponseEntity.ok(recommandations);
    }

    /**
     * Endpoint pentru a cere sugestii specifice unei locații (pin pe hartă).
     * Trigger-uiește un proces asincron care va trimite notificări prin WebSocket.
     */
    @PostMapping("/suggestions")
    public ResponseEntity<Void> getSuggestionsForLocation(@RequestBody Map<String, String> payload) {
        String locationName = payload.get("locationName");
        String cityName = payload.get("cityName");
        
        if (locationName != null && cityName != null) {
            recommandationService.getSuggestionsForLocation(locationName, cityName);
        }
        
        return ResponseEntity.accepted().build();
    }
}
