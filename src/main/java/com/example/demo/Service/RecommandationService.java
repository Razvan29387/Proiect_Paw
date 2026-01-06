package com.example.demo.Service;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.DTO.UserLocationDto;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommandationService {

    private final CityRepository cityRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String groqApiKey;
    private final String groqModel = "openai/gpt-oss-120b";

    public RecommandationService(CityRepository cityRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.groqApiKey = System.getenv("GROQ_API_KEY");
    }

    public List<RecommandationDto> getRecommandations(String cityName) {
        String normalizedCityName = cityName.trim();
        
        List<RecommandationDto> photonPlaces = fetchRealPlacesFromPhoton(normalizedCityName);
        List<RecommandationDto> aiRecommendations = fetchRecommendationsFromAI(normalizedCityName);
        
        List<RecommandationDto> balancedList = buildBalancedList(aiRecommendations, photonPlaces, normalizedCityName);

        // FALLBACK DE URGENȚĂ: Dacă lista e goală, returnăm ce a dat AI-ul (nevalidat)
        // Mai bine riscăm o halucinație decât să nu afișăm nimic.
        if (balancedList.isEmpty() && !aiRecommendations.isEmpty()) {
            System.out.println("Warning: No validated places found for " + cityName + ". Returning raw AI list.");
            return aiRecommendations;
        }

        return balancedList;
    }

    private String correctCategory(String name, String originalCategory) {
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("hotel") || lowerName.contains("pensiune") || lowerName.contains("vila") || 
            lowerName.contains("hostel") || lowerName.contains("resort") || lowerName.contains("casa")) {
            return "Hotel";
        }
        
        if (lowerName.contains("restaurant") || lowerName.contains("bistro") || lowerName.contains("pub") || 
            lowerName.contains("cafe") || lowerName.contains("pizzeria") || lowerName.contains("trattoria") || 
            lowerName.contains("bar") || lowerName.contains("fast food") || lowerName.contains("sushi") || lowerName.contains("ramen")) {
            return "Restaurant";
        }
        
        return originalCategory;
    }

    private List<RecommandationDto> buildBalancedList(List<RecommandationDto> aiList, List<RecommandationDto> photonList, String cityName) {
        List<RecommandationDto> finalResult = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        List<RecommandationDto> correctedPhotonList = photonList.stream()
            .map(p -> new RecommandationDto(null, p.name(), p.englishName(), p.description(), correctCategory(p.name(), p.category()), p.lat(), p.lon()))
            .collect(Collectors.toList());

        List<RecommandationDto> photonAttractions = correctedPhotonList.stream().filter(p -> p.category().equals("Tourist Attraction")).collect(Collectors.toList());
        List<RecommandationDto> photonHotels = correctedPhotonList.stream().filter(p -> p.category().equals("Hotel")).collect(Collectors.toList());
        List<RecommandationDto> photonRestaurants = correctedPhotonList.stream().filter(p -> p.category().equals("Restaurant")).collect(Collectors.toList());

        List<RecommandationDto> validAttractions = new ArrayList<>();
        List<RecommandationDto> validHotels = new ArrayList<>();
        List<RecommandationDto> validRestaurants = new ArrayList<>();

        for (RecommandationDto aiRec : aiList) {
            Optional<RecommandationDto> match = correctedPhotonList.stream()
                .filter(p -> isSimilarName(p.name(), aiRec.name()))
                .findFirst();
            
            if (match.isPresent()) {
                RecommandationDto photonRec = match.get();
                String finalCategory = correctCategory(photonRec.name(), photonRec.category());
                
                RecommandationDto validatedRec = new RecommandationDto(
                    null, photonRec.name(), aiRec.englishName(), aiRec.description(), finalCategory,
                    photonRec.lat(), photonRec.lon()
                );
                
                if (finalCategory.equals("Tourist Attraction")) validAttractions.add(validatedRec);
                else if (finalCategory.equals("Hotel")) validHotels.add(validatedRec);
                else if (finalCategory.equals("Restaurant")) validRestaurants.add(validatedRec);
                
                addedNames.add(photonRec.name().toLowerCase());
            }
        }

        smartBackfill(validAttractions, photonAttractions, 15, "Tourist Attraction", cityName, addedNames);
        smartBackfill(validHotels, photonHotels, 6, "Hotel", cityName, addedNames);
        smartBackfill(validRestaurants, photonRestaurants, 5, "Restaurant", cityName, addedNames);

        finalResult.addAll(validAttractions);
        finalResult.addAll(validHotels);
        finalResult.addAll(validRestaurants);
        
        return finalResult;
    }

    private void smartBackfill(List<RecommandationDto> targetList, List<RecommandationDto> sourceList, int limit, String category, String cityName, Set<String> addedNames) {
        int needed = limit - targetList.size();
        if (needed <= 0) return;

        List<RecommandationDto> candidates = sourceList.stream()
                .filter(p -> !addedNames.contains(p.name().toLowerCase()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return;

        if (candidates.size() <= needed) {
            for (RecommandationDto rec : candidates) {
                targetList.add(rec);
                addedNames.add(rec.name().toLowerCase());
            }
            return;
        }

        List<RecommandationDto> aiCandidates = candidates.size() > 50 ? candidates.subList(0, 50) : candidates;
        List<RecommandationDto> selectedByAI = callBackfillAI(aiCandidates, needed, category, cityName);
        
        for (RecommandationDto aiRec : selectedByAI) {
            Optional<RecommandationDto> original = candidates.stream()
                    .filter(p -> p.name().equalsIgnoreCase(aiRec.name()))
                    .findFirst();
            
            if (original.isPresent()) {
                targetList.add(new RecommandationDto(
                    null, aiRec.name(), aiRec.englishName(), aiRec.description(), category,
                    original.get().lat(), original.get().lon()
                ));
                addedNames.add(aiRec.name().toLowerCase());
            }
        }
        
        while (targetList.size() < limit && !candidates.isEmpty()) {
             RecommandationDto rec = candidates.remove(0);
             if (!addedNames.contains(rec.name().toLowerCase())) {
                 targetList.add(rec);
                 addedNames.add(rec.name().toLowerCase());
             }
        }
    }

    private List<RecommandationDto> callBackfillAI(List<RecommandationDto> candidates, int count, String category, String cityName) {
        String candidatesList = candidates.stream().map(RecommandationDto::name).collect(Collectors.joining(", "));
        
        String prompt = String.format(
                "I have a list of additional %s options in %s: [%s]. " +
                "Please select the BEST %d items from this list. " +
                "Write a short description for each. " +
                "Return JSON with 'recommendations': [{'name', 'englishName', 'description', 'category'}]. " +
                "Use EXACT names from the list.",
                category, cityName, candidatesList, count);

        return callGroqAI(prompt);
    }

    private List<RecommandationDto> callGroqAI(String prompt) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.groqApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.groqModel);
        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        
        try {
            requestBody.putObject("response_format").put("type", "json_object");
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode content = objectMapper.readTree(response).path("choices").get(0).path("message").path("content");
            String jsonStr = content.isObject() ? content.toString() : content.asText();
            
            if (jsonStr.contains("```json")) jsonStr = jsonStr.replace("```json", "").replace("```", "");
            int firstBrace = jsonStr.indexOf("{");
            int lastBrace = jsonStr.lastIndexOf("}");
            if (firstBrace != -1 && lastBrace != -1) jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);

            return objectMapper.convertValue(objectMapper.readTree(jsonStr).path("recommendations"), new TypeReference<List<RecommandationDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean isSimilarName(String name1, String name2) {
        String n1 = name1.toLowerCase();
        String n2 = name2.toLowerCase();
        return n1.contains(n2) || n2.contains(n1);
    }

    private List<RecommandationDto> fetchRealPlacesFromPhoton(String city) {
        List<RecommandationDto> places = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        try {
            searchPhotonCategory(city, "tourism", places, uniqueNames);
            searchPhotonCategory(city, "attraction", places, uniqueNames);
            searchPhotonCategory(city, "museum", places, uniqueNames);
            searchPhotonCategory(city, "park", places, uniqueNames);
            searchPhotonCategory(city, "church", places, uniqueNames);
            searchPhotonCategory(city, "historic", places, uniqueNames);
            searchPhotonCategory(city, "tower", places, uniqueNames);
            searchPhotonCategory(city, "bastion", places, uniqueNames);
            searchPhotonCategory(city, "monument", places, uniqueNames);
            
            searchPhotonCategory(city, "restaurant", places, uniqueNames);
            searchPhotonCategory(city, "hotel", places, uniqueNames);
        } catch (Exception e) {}
        return places;
    }

    private void searchPhotonCategory(String city, String keyword, List<RecommandationDto> places, Set<String> uniqueNames) {
        try {
            String query = URLEncoder.encode(keyword + " " + city, StandardCharsets.UTF_8);
            String url = "https://photon.komoot.io/api/?q=" + query + "&limit=100";
            
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            
            for (JsonNode feature : root.path("features")) {
                JsonNode props = feature.path("properties");
                JsonNode coords = feature.path("geometry").path("coordinates");
                
                String name = props.path("name").asText();
                String foundCity = props.path("city").asText().toLowerCase();
                String foundTown = props.path("town").asText().toLowerCase();
                
                if (!name.isEmpty() && !name.equalsIgnoreCase(city) && 
                   (foundCity.contains(city.toLowerCase()) || foundTown.contains(city.toLowerCase()))) {
                    
                    if (uniqueNames.contains(name.toLowerCase())) continue;
                    
                    String category = "Tourist Attraction";
                    String osmKey = props.path("osm_key").asText();
                    String osmValue = props.path("osm_value").asText();
                    String nameLower = name.toLowerCase();

                    if (nameLower.contains("mall") || nameLower.contains("shopping") || nameLower.contains("supermarket")) continue;

                    if (osmKey.equals("amenity") && (osmValue.equals("restaurant") || osmValue.equals("fast_food") || osmValue.equals("cafe"))) {
                        category = "Restaurant";
                    } else if (osmKey.equals("tourism") && (osmValue.equals("hotel") || osmValue.equals("guest_house"))) {
                        category = "Hotel";
                    }
                    
                    uniqueNames.add(nameLower);
                    places.add(new RecommandationDto(
                        null, name, name, "Discover this place in " + city, category, 
                        coords.get(1).asDouble(), coords.get(0).asDouble()
                    ));
                }
            }
        } catch (Exception e) {}
    }

    private List<RecommandationDto> fetchRecommendationsFromAI(String cityName) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
        String prompt = String.format(
                "Provide a list of 30 recommendations for '%s'. " +
                "Include: 15 Tourist Attractions, 5 Restaurants, 6 Hotels. " +
                "Use ORIGINAL LOCAL NAMES. " +
                "Return JSON with 'recommendations': [{'name', 'englishName', 'description', 'category'}].",
                cityName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.groqApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.groqModel);
        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        
        try {
            requestBody.putObject("response_format").put("type", "json_object");
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode content = objectMapper.readTree(response).path("choices").get(0).path("message").path("content");
            String jsonStr = content.isObject() ? content.toString() : content.asText();
            
            if (jsonStr.contains("```json")) jsonStr = jsonStr.replace("```json", "").replace("```", "");
            int firstBrace = jsonStr.indexOf("{");
            int lastBrace = jsonStr.lastIndexOf("}");
            if (firstBrace != -1 && lastBrace != -1) jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);

            return objectMapper.convertValue(objectMapper.readTree(jsonStr).path("recommendations"), new TypeReference<List<RecommandationDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String getCityNotification(String cityName) { return "Welcome to " + cityName; }
    public String getLiveRecommendation(UserLocationDto location) { return "Explore " + location.locationName(); }
}
