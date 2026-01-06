package com.example.demo.Controllers;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/Recommandations")
public class RecommandationController {

    private final RecommandationService recommandationService;

    public RecommandationController(RecommandationService recommandationService) {
        this.recommandationService = recommandationService;
    }

    /**
     * Endpoint-ul principal: Obține recomandările pentru un oraș specificat.
     * Dacă orașul nu există în baza de date, serviciul îl va adăuga.
     * Ex: GET /api/v1/Recommandations/Bucuresti
     */
    @GetMapping("/{cityName}")
    public ResponseEntity<List<RecommandationDto>> getRecommandationsForCity(
            @PathVariable("cityName") String city) {
        List<RecommandationDto> recommandations = recommandationService.getRecommandations(city);
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
