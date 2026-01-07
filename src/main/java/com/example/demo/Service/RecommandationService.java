package com.example.demo.Service;

import com.example.demo.DTO.RecommandationDto;
import com.example.demo.DTO.UserLocationDto;
import com.example.demo.Entity.City;
import com.example.demo.Entity.Recommendation;
import com.example.demo.Repository.CityRepository;
import com.example.demo.Repository.RecommendationRepository;
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
    private final RecommendationRepository recommendationRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String groqApiKey;
    private final String groqModel = "openai/gpt-oss-120b";

    public RecommandationService(CityRepository cityRepository, RecommendationRepository recommendationRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.recommendationRepository = recommendationRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.groqApiKey = System.getenv("GROQ_API_KEY");
    }

    public List<RecommandationDto> getRecommandations(String cityName) {
        String normalizedCityName = cityName.trim();
        String simpleCityName = extractSimpleCityName(normalizedCityName);
        
        City city = saveCityIfNotExists(normalizedCityName);
        
        // COMENTAT: Nu mai returnăm din DB pentru a forța generarea proaspătă de fiecare dată
        /*
        if (!city.getRecommendations().isEmpty()) {
            return city.getRecommendations().stream()
                .map(r -> new RecommandationDto(r.getId(), r.getName(), r.getName(), r.getDescription(), r.getCategory(), r.getLatitude(), r.getLongitude(), r.getWikipediaLink(), r.getImageUrl()))
                .collect(Collectors.toList());
        }
        */

        List<RecommandationDto> photonPlaces = fetchRealPlacesFromPhoton(simpleCityName);
        List<RecommandationDto> aiRecommendations = fetchRecommendationsFromAI(simpleCityName);
        List<RecommandationDto> balancedList = buildBalancedList(aiRecommendations, photonPlaces, simpleCityName);

        List<RecommandationDto> finalSavedList = new ArrayList<>();
        
        for (RecommandationDto dto : balancedList) {
            String wikiLink = null;
            String imageUrl = null;
            String description = dto.description();

            if ("Tourist Attraction".equals(dto.category())) {
                WikiData wikiData = searchWikipediaData(dto.name(), simpleCityName);
                if (wikiData != null) {
                    wikiLink = wikiData.url;
                    imageUrl = wikiData.imageUrl;
                    if (wikiData.extract != null && !wikiData.extract.isEmpty()) {
                        description = wikiData.extract;
                    }
                }
            }

            saveRecommendation(dto, city, wikiLink, imageUrl, description);
            
            finalSavedList.add(new RecommandationDto(
                dto.id(), dto.name(), dto.englishName(), description, dto.category(), 
                dto.lat(), dto.lon(), wikiLink, imageUrl
            ));
        }

        return finalSavedList;
    }

    private String extractSimpleCityName(String fullName) {
        if (fullName.contains(",")) {
            return fullName.split(",")[0].trim();
        }
        return fullName;
    }

    private City saveCityIfNotExists(String cityName) {
        return cityRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(cityName))
                .findFirst()
                .orElseGet(() -> cityRepository.save(new City(cityName)));
    }

    private void saveRecommendation(RecommandationDto dto, City city, String wikiLink, String imageUrl, String description) {
        try {
            Optional<Recommendation> existing = recommendationRepository.findByNameAndCityId(dto.name(), city.getId());
            if (existing.isEmpty()) {
                Recommendation rec = new Recommendation(
                    dto.name(), description, dto.category(), wikiLink, imageUrl, dto.lat(), dto.lon(), city
                );
                recommendationRepository.save(rec);
            } else {
                // Putem actualiza dacă vrem, dar momentan lăsăm așa
            }
        } catch (Exception e) {
            System.err.println("Error saving recommendation: " + e.getMessage());
        }
    }

    private static class WikiData {
        String url;
        String imageUrl;
        String extract;
        public WikiData(String url, String imageUrl, String extract) {
            this.url = url;
            this.imageUrl = imageUrl;
            this.extract = extract;
        }
    }

    private WikiData searchWikipediaData(String name, String city) {
        try {
            String query = URLEncoder.encode(name + " " + city, StandardCharsets.UTF_8);
            String searchUrl = "https://ro.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + query + "&format=json";
            String searchResponse = restTemplate.getForObject(searchUrl, String.class);
            JsonNode searchRoot = objectMapper.readTree(searchResponse);
            
            String title = null;
            if (searchRoot.path("query").path("search").size() > 0) {
                title = searchRoot.path("query").path("search").get(0).path("title").asText();
                if (title.equalsIgnoreCase(city)) {
                    if (searchRoot.path("query").path("search").size() > 1) {
                        title = searchRoot.path("query").path("search").get(1).path("title").asText();
                    } else {
                        return null;
                    }
                }
            } else {
                query = URLEncoder.encode(name, StandardCharsets.UTF_8);
                searchUrl = "https://ro.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + query + "&format=json";
                searchResponse = restTemplate.getForObject(searchUrl, String.class);
                searchRoot = objectMapper.readTree(searchResponse);
                if (searchRoot.path("query").path("search").size() > 0) {
                    title = searchRoot.path("query").path("search").get(0).path("title").asText();
                    if (title.equalsIgnoreCase(city)) return null;
                }
            }

            if (title != null) {
                String detailsUrl = "https://ro.wikipedia.org/w/api.php?action=query&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&prop=pageimages|extracts&pithumbsize=300&exintro=1&explaintext=1&format=json";
                String detailsResponse = restTemplate.getForObject(detailsUrl, String.class);
                JsonNode detailsRoot = objectMapper.readTree(detailsResponse);
                JsonNode pages = detailsRoot.path("query").path("pages");
                
                if (pages.fieldNames().hasNext()) {
                    JsonNode page = pages.get(pages.fieldNames().next());
                    String imageUrl = page.path("thumbnail").path("source").asText(null);
                    String extract = page.path("extract").asText(null);
                    if (extract != null && extract.length() > 200) extract = extract.substring(0, 200) + "...";
                    
                    return new WikiData(
                        "https://ro.wikipedia.org/wiki/" + URLEncoder.encode(title, StandardCharsets.UTF_8),
                        imageUrl,
                        extract
                    );
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private String correctCategory(String name, String originalCategory) {
        String lowerName = name.toLowerCase();
        if (lowerName.contains("hotel") || lowerName.contains("pensiune") || lowerName.contains("vila") || lowerName.contains("hostel")) return "Hotel";
        if (lowerName.contains("restaurant") || lowerName.contains("bistro") || lowerName.contains("pub") || lowerName.contains("cafe") || lowerName.contains("pizzeria")) return "Restaurant";
        return originalCategory;
    }

    private List<RecommandationDto> buildBalancedList(List<RecommandationDto> aiList, List<RecommandationDto> photonList, String cityName) {
        List<RecommandationDto> finalResult = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        List<RecommandationDto> correctedPhotonList = photonList.stream()
            .map(p -> new RecommandationDto(null, p.name(), p.englishName(), p.description(), correctCategory(p.name(), p.category()), p.lat(), p.lon(), null, null))
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
                    photonRec.lat(), photonRec.lon(), null, null
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
                    original.get().lat(), original.get().lon(), null, null
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
        requestBody.put("temperature", 0.8);
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
            searchPhotonCategory(city, "attraction", places, uniqueNames);
            searchPhotonCategory(city, "historic", places, uniqueNames);
            searchPhotonCategory(city, "monument", places, uniqueNames);
            searchPhotonCategory(city, "tourism", places, uniqueNames);
            searchPhotonCategory(city, "landmark", places, uniqueNames);
            searchPhotonCategory(city, "cathedral", places, uniqueNames);
            searchPhotonCategory(city, "castle", places, uniqueNames);
            searchPhotonCategory(city, "museum", places, uniqueNames);
            searchPhotonCategory(city, "park", places, uniqueNames);
            searchPhotonCategory(city, "church", places, uniqueNames);
            searchPhotonCategory(city, "tower", places, uniqueNames);
            searchPhotonCategory(city, "bastion", places, uniqueNames);
            
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
                if (!name.isEmpty() && !name.equalsIgnoreCase(city) && (foundCity.contains(city.toLowerCase()) || foundTown.contains(city.toLowerCase()))) {
                    if (uniqueNames.contains(name.toLowerCase())) continue;
                    String category = "Tourist Attraction";
                    String osmKey = props.path("osm_key").asText();
                    String osmValue = props.path("osm_value").asText();
                    String nameLower = name.toLowerCase();
                    if (nameLower.contains("mall") || nameLower.contains("shopping") || nameLower.contains("supermarket")) continue;
                    if (osmKey.equals("amenity") && (osmValue.equals("restaurant") || osmValue.equals("fast_food") || osmValue.equals("cafe"))) category = "Restaurant";
                    else if (osmKey.equals("tourism") && (osmValue.equals("hotel") || osmValue.equals("guest_house"))) category = "Hotel";
                    uniqueNames.add(nameLower);
                    places.add(new RecommandationDto(null, name, name, "Discover this place in " + city, category, coords.get(1).asDouble(), coords.get(0).asDouble(), null, null));
                }
            }
        } catch (Exception e) {}
    }

    private List<RecommandationDto> fetchRecommendationsFromAI(String cityName) {
        String prompt = String.format("Provide a list of 30 recommendations for '%s'. Include: 15 Tourist Attractions, 5 Restaurants, 6 Hotels. Use ORIGINAL LOCAL NAMES. Return JSON with 'recommendations': [{'name', 'englishName', 'description', 'category'}].", cityName);
        return callGroqAI(prompt);
    }

    public String getCityNotification(String cityName) { return "Welcome to " + cityName; }
    public String getLiveRecommendation(UserLocationDto location) { return "Explore " + location.locationName(); }
}
