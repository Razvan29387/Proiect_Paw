package com.example.demo.Controllers;



import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/Recommandations")
// @CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // Decomentează dacă ai probleme de CORS
public class RecommandationController {

    private final RecommandationService RecommandationService;

    // Folosim injecție prin constructor (recomandat)
    public RecommandationController(RecommandationService RecommandationService) {
        this.RecommandationService = RecommandationService;
    }

    /**
     * ENDPOINT 1: Obține lista de orașe disponibile.
     * * Aceasta este cheia! Returnând List<String>, Spring Boot (cu Jackson)
     * va serializa automat asta într-un array JSON: ["București", "Cluj-Napoca", ...]
     * Acest lucru va rezolva eroarea "cities.map is not a function" din React.
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> getAvailableCities() {
        List<String> cities = RecommandationService.getCityNames();
        return ResponseEntity.ok(cities);
    }

    /**
     * ENDPOINT 2: Obține recomandările pentru un oraș specificat.
     * * Vom folosi un @PathVariable pentru a prelua numele orașului din URL.
     * Ex: GET /api/v1/Recommandations/Bucuresti
     */
    @GetMapping("/{cityName}")
    public ResponseEntity<List<RecommandationDto>> getRecommandationsForCity(
            @PathVariable("cityName") String city) {

        List<RecommandationDto> Recommandations = RecommandationService.getRecommandations(city);
        return ResponseEntity.ok(Recommandations);
    }
}
