package com.example.demo.Controllers;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recommendation")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class RecommendationController {

    private final RecommandationService recommandationService;

    public RecommendationController(RecommandationService recommandationService) {
        this.recommandationService = recommandationService;
    }

    @GetMapping
    public List<RecommandationDto> getRecommendations(@RequestParam String city) {
        return recommandationService.getRecommandations(city);
    }

    // Endpoint nou pentru a evita eroarea de CORS din frontend
    @GetMapping("/geocode")
    public Map<String, Double> geocodeCity(@RequestParam String city) {
        double[] coords = recommandationService.getCityCoordinates(city);
        if (coords != null) {
            return Map.of("lat", coords[0], "lon", coords[1]);
        }
        return null;
    }
}
