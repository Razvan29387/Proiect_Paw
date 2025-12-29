package com.example.demo.Service;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.Entity.City;
import com.example.demo.Repository.CityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecommandationService {

    private final CityRepository cityRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String groqApiKey;
    private final String groqModel = "llama-3.3-70b-versatile"; // Model stabil

    public RecommandationService(CityRepository cityRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;

        this.groqApiKey = System.getenv("GROQ_API_KEY");

        if (this.groqApiKey == null || this.groqApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("\n\n!!! EROARE FATALĂ !!!\n>>> Variabila de mediu 'GROQ_API_KEY' nu a fost găsită sau este goală.\n>>> Asigură-te că este setată corect în configurația de rulare a IntelliJ.\n");
        }
    }

    public List<RecommandationDto> getRecommandations(String cityName) {
        // Normalizăm numele orașului pentru consistență
        String normalizedCityName = cityName.trim();

        // Verificăm dacă orașul există deja în baza de date
        boolean cityExists = cityRepository.findAll().stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(normalizedCityName));

        // Dacă nu există, îl validăm cu AI-ul și apoi îl salvăm
        if (!cityExists) {
            if (!isValidCity(normalizedCityName)) {
                throw new IllegalArgumentException("The city '" + normalizedCityName + "' does not appear to be a valid city.");
            }
            saveCity(normalizedCityName);
        }

        // Obținem recomandările
        return fetchRecommendationsFromAI(normalizedCityName);
    }

    private List<RecommandationDto> fetchRecommendationsFromAI(String cityName) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

        // Prompt modificat pentru a cere răspunsul în ENGLEZĂ
        String prompt = String.format(
                "Please act as a travel guide. Provide a list of travel recommendations for the city '%s'. " +
                "Include tourist attractions, restaurants, guesthouses, and hotels. " +
                "Return the response as a valid JSON object, without any other text before or after. " +
                "The object must contain a single key named 'recommendations', which is a JSON array. " +
                "Each object in the array must have the following fields: " +
                "'name' (the name of the attraction/location in English), " +
                "'description' (a concise and attractive description in English), " +
                "'category' (one of: 'Tourist Attraction', 'Restaurant', 'Guesthouse', 'Hotel').",
                cityName
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.groqApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.groqModel);
        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        
        try {
            requestBody.putObject("response_format").put("type", "json_object");
        } catch (Exception e) {
            // Ignorăm
        }

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode root = objectMapper.readTree(response);
            String generatedText = root.path("choices").get(0).path("message").path("content").asText();
            
            JsonNode recommendationsNode = objectMapper.readTree(generatedText).path("recommendations");

            if (recommendationsNode.isArray()) {
                return objectMapper.convertValue(recommendationsNode, new TypeReference<List<RecommandationDto>>() {});
            } else {
                System.err.println("Groq API did not return a JSON object with a 'recommendations' array as expected.");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            System.err.println("Error calling Groq API or parsing response: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isValidCity(String cityName) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
        String prompt = String.format("Is '%s' a real city name? Answer with only YES or NO.", cityName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.groqApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.groqModel);
        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode root = objectMapper.readTree(response);
            String answer = root.path("choices").get(0).path("message").path("content").asText().trim().toUpperCase();
            
            return answer.contains("YES");
        } catch (Exception e) {
            System.err.println("Error validating city: " + e.getMessage());
            return false;
        }
    }

    private void saveCity(String cityName) {
        City newCity = new City(cityName);
        cityRepository.save(newCity);
        System.out.println("New city saved to database: " + cityName);
    }


}
