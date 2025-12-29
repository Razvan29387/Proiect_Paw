package com.example.demo.Controllers;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Service.RecommandationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
