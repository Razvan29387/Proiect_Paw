package com.example.demo.Service;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Entity.City;
import com.example.demo.Repository.CityRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecommandationService {

    private final CityRepository cityRepository;

    // Injectăm CityRepository prin constructor
    public RecommandationService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    // Mock data pentru recomandări (acesta va fi următorul pas de refactorizat)
    private static final Map<String, List<RecommandationDto>> mockDb = Map.of(
            "București", List.of(
                    new RecommandationDto(1L, "Palatul Parlamentului", "A doua cea mai mare clădire administrativă din lume.", "Obiectiv Turistic"),
                    new RecommandationDto(2L, "Muzeul Național de Artă", "Găzduit în fostul Palat Regal.", "Muzeu"),
                    new RecommandationDto(3L, "Grădina Botanică", "O plimbare relaxantă printre specii de plante rare.", "Parc")
            ),
            "Cluj-Napoca", List.of(
                    new RecommandationDto(4L, "Grădina Botanică 'Alexandru Borza'", "Una dintre cele mai frumoase din România.", "Parc"),
                    new RecommandationDto(5L, "Piața Unirii", "Centrul istoric al orașului.", "Obiectiv Turistic"),
                    new RecommandationDto(6L, "Fabrica de Pensule", "Centru de artă contemporană.", "Cultură")
            ),
            "Brașov", List.of(
                    new RecommandationDto(7L, "Biserica Neagră", "Simbolul orașului Brașov.", "Obiectiv Turistic"),
                    new RecommandationDto(8L, "Tâmpa", "Drumeție sau telecabină pentru o priveliște panoramică.", "Natură")
            )
    );

    /**
     * Logica pentru Endpoint 1: Returnează numele orașelor din baza de date
     */
    public List<String> getCityNames() {
        // Preluăm toate entitățile City, le transformăm într-un stream,
        // extragem numele fiecăreia și le colectăm într-o listă.
        return cityRepository.findAll().stream()
                .map(City::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Logica pentru Endpoint 2: Returnează recomandările pentru un oraș
     */
    public List<RecommandationDto> getRecommandations(String cityName) {
        return mockDb.getOrDefault(cityName, List.of());
    }
}
