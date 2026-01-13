/*package com.example.demo.Config;

import com.example.demo.Entity.City;
import com.example.demo.Repository.CityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(CityRepository cityRepository) {
        return args -> {
            // Pasul 1: Citim orașele din fișierul JSON
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<City>> typeReference = new TypeReference<List<City>>(){};
            InputStream inputStream = new ClassPathResource("cities.json").getInputStream();
            List<City> citiesFromJson;
            try {
                citiesFromJson = mapper.readValue(inputStream, typeReference);
            } catch (Exception e) {
                System.out.println("Unable to read cities.json: " + e.getMessage());
                return; // Oprim execuția dacă fișierul nu poate fi citit
            }

            // Pasul 2: Citim numele orașelor deja existente în baza de date
            Set<String> existingCityNames = cityRepository.findAll()
                    .stream()
                    .map(City::getName)
                    .collect(Collectors.toSet());

            // Pasul 3: Filtrăm și adăugăm doar orașele noi
            List<City> newCities = citiesFromJson.stream()
                    .filter(city -> !existingCityNames.contains(city.getName()))
                    .collect(Collectors.toList());

            if (!newCities.isEmpty()) {
                cityRepository.saveAll(newCities);
                System.out.println(newCities.size() + " new cities have been saved to the database!");
            } else {
                System.out.println("City list is up to date. No new cities were added.");
            }
        };
    }
}
*/