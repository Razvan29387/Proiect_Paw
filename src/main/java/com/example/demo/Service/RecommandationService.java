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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecommandationService {

    private final CityRepository cityRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate; // Injectăm template-ul pentru WebSocket
    private final String groqApiKey;
    private final String groqModel = "llama-3.3-70b-versatile"; // Model stabil

    public RecommandationService(CityRepository cityRepository, RestTemplate restTemplate, ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.cityRepository = cityRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;

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
        List<RecommandationDto> recommendations = fetchRecommendationsFromAI(normalizedCityName);

        // Validăm recomandările cu Photon API
        List<RecommandationDto> validatedRecommendations = recommendations.stream()
                .filter(rec -> verifyLocationWithPhoton(rec.name(), rec.localName(), normalizedCityName))
                .collect(Collectors.toList());

        // Trimitem notificarea prin WebSocket
        sendRecommendationNotification(normalizedCityName, validatedRecommendations.size());

        return validatedRecommendations;
    }

    // Metodă nouă pentru a obține sugestii specifice unei zone
    public void getSuggestionsForLocation(String locationName, String cityName) {
        // Rulăm asincron pentru a nu bloca request-ul (simulat aici prin faptul că returnăm void și trimitem pe socket)
        new Thread(() -> {
            List<RecommandationDto> suggestions = fetchSuggestionsFromAI(locationName, cityName);
            
            // Validăm și sugestiile
            List<RecommandationDto> validatedSuggestions = suggestions.stream()
                    .filter(rec -> verifyLocationWithPhoton(rec.name(), rec.localName(), cityName))
                    .collect(Collectors.toList());

            if (!validatedSuggestions.isEmpty()) {
                // Trimitem sugestiile prin WebSocket
                String message = String.format("Found %d hidden gems near %s: %s", 
                    validatedSuggestions.size(), 
                    locationName, 
                    validatedSuggestions.stream().map(RecommandationDto::name).collect(Collectors.joining(", ")));
                
                messagingTemplate.convertAndSend("/topic/recommendations", message);
            }
        }).start();
    }

    private void sendRecommendationNotification(String cityName, int count) {
        String message = String.format("New recommendations available for %s! Found %d places.", cityName, count);
        // Trimitem mesajul pe topicul /topic/recommendations
        messagingTemplate.convertAndSend("/topic/recommendations", message);
    }

    private List<RecommandationDto> fetchRecommendationsFromAI(String cityName) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

        // Prompt modificat pentru a cere și numele local
        String prompt = String.format(
                "Please act as a travel guide. Provide a comprehensive list of at least 20 travel recommendations for the city '%s'. " +
                "Include a mix of popular tourist attractions, top-rated restaurants, cozy guesthouses, and hotels. " +
                "Return the response as a valid JSON object, without any other text before or after. " +
                "The object must contain a single key named 'recommendations', which is a JSON array. " +
                "Each object in the array must have the following fields: " +
                "'name' (the name of the attraction/location in English), " +
                "'localName' (the name of the attraction/location in the local language, e.g., French for Paris, Romanian for Baia Mare. If same as English, repeat it), " +
                "'description' (a concise and attractive description in English), " +
                "'category' (one of: 'Tourist Attraction', 'Restaurant', 'Guesthouse', 'Hotel').",
                cityName
        );

        return callGroqApi(prompt);
    }

    private List<RecommandationDto> fetchSuggestionsFromAI(String locationName, String cityName) {
        String prompt = String.format(
                "I am currently at '%s' in '%s'. Please suggest 3 hidden gems or interesting places strictly within walking distance (max 500m) of this location that are NOT usually in the top tourist guides. " +
                "Return the response as a valid JSON object, without any other text. " +
                "The object must contain a single key named 'recommendations', which is a JSON array. " +
                "Each object in the array must have: 'name', 'localName', 'description', 'category'.",
                locationName, cityName
        );
        return callGroqApi(prompt);
    }

    private List<RecommandationDto> callGroqApi(String prompt) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
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
                return Collections.emptyList();
            }

        } catch (Exception e) {
            System.err.println("Error calling Groq API: " + e.getMessage());
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

    /**
     * Verifică dacă o locație există în orașul specificat folosind Photon API.
     * Încearcă atât numele în engleză cât și cel local.
     */
    private boolean verifyLocationWithPhoton(String placeName, String localName, String cityName) {
        // 1. Încercăm cu numele în engleză
        if (verifySingleName(placeName, cityName)) return true;
        
        // 2. Dacă e diferit, încercăm cu numele local
        if (localName != null && !localName.equalsIgnoreCase(placeName)) {
            if (verifySingleName(localName, cityName)) return true;
        }
        
        return false;
    }

    private boolean verifySingleName(String name, String cityName) {
        // 1. Curățăm numele de prefixe comune
        String cleanName = name.replaceAll("^(The|Le|La|L'|Hotel|Restaurant)\\s+", "");
        
        // 2. Încercăm prima dată cu numele complet + oraș
        if (checkPhoton(name + " " + cityName, cityName)) return true;
        
        // 3. Încercăm cu numele curățat + oraș (dacă e diferit)
        if (!cleanName.equals(name)) {
            if (checkPhoton(cleanName + " " + cityName, cityName)) return true;
        }
        
        // 4. Încercăm doar cu numele locului (pentru landmark-uri faimoase)
        return checkPhoton(name, cityName);
    }

    private boolean checkPhoton(String query, String expectedCity) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://photon.komoot.io/api/")
                    .queryParam("q", query)
                    .queryParam("limit", 1)
                    .toUriString();

            System.out.println("Photon Query: " + query); // LOGGING

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");

            if (features.isArray() && features.size() > 0) {
                JsonNode properties = features.get(0).path("properties");
                
                String foundCity = properties.path("city").asText("");
                String foundTown = properties.path("town").asText("");
                String foundVillage = properties.path("village").asText("");
                String foundCountry = properties.path("country").asText("");
                String foundName = properties.path("name").asText("");

                System.out.println("  -> Found: " + foundName + " in " + foundCity + ", " + foundCountry); // LOGGING

                // Verificare:
                // 1. Orașul se potrivește (case-insensitive)
                boolean cityMatch = foundCity.toLowerCase().contains(expectedCity.toLowerCase()) ||
                                    foundTown.toLowerCase().contains(expectedCity.toLowerCase()) ||
                                    foundVillage.toLowerCase().contains(expectedCity.toLowerCase());
                
                if (cityMatch) return true;

                // 2. Dacă orașul nu e explicit, dar țara e corectă
                if (!foundCountry.isEmpty()) {
                    return true;
                }
            } else {
                System.out.println("  -> No results.");
            }
        } catch (Exception e) {
            System.err.println("Error calling Photon: " + e.getMessage());
            return true; // Fail-open
        }
        return false;
    }
}
