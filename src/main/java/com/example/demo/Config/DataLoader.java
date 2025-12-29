package com.example.demo.Config;

import com.example.demo.Entity.City;
import com.example.demo.Repository.CityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final CityRepository cityRepository;
    private final ObjectMapper objectMapper;

    public DataLoader(CityRepository cityRepository, ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        // Verificăm dacă baza de date este goală pentru a nu insera duplicate
        if (cityRepository.count() == 0) {
            InputStream inputStream = new ClassPathResource("cities.json").getInputStream();
            List<City> cities = objectMapper.readValue(inputStream, new TypeReference<List<City>>() {});
            cityRepository.saveAll(cities);
            System.out.println(cities.size() + " cities loaded into database.");
        }
    }
}