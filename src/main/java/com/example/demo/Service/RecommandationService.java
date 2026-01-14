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

@Service
public class RecommandationService {

    private final CityRepository cityRepository;
    private final RecommendationRepository recommendationRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String groqApiKey;
    private final String groqModel = "llama-3.3-70b-versatile";

    // CONFIGURAȚIE: Numărul exact de recomandări per categorie
    private static final int MAX_HOTELS = 2;
    private static final int MAX_RESTAURANTS = 2;
    private static final int MAX_ATTRACTIONS = 10;

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

        // 1. Asigurăm că orașul există în DB (pentru a putea salva recomandările ulterior)
        City city = saveCityIfNotExists(normalizedCityName);

        // 2. ÎNTOTDEAUNA interogăm AI-ul pentru recomandări proaspete
        // Nu verificăm DB-ul pentru a returna date existente.
        List<RecommandationDto> aiPopularPlaces = fetchMostPopularFromAI(simpleCityName);

        // 3. Verificăm existența reală și obținem coordonatele
        List<RecommandationDto> verifiedPlaces = verifyAndGetCoordinates(aiPopularPlaces, simpleCityName);

        // 4. Îmbogățim cu date Wikipedia și SALVĂM în DB (pentru istoric/analiză viitoare)
        List<RecommandationDto> finalResultList = new ArrayList<>();

        for (RecommandationDto dto : verifiedPlaces) {
            String wikiLink = null;
            String imageUrl = null;
            String description = dto.description();

            // Căutăm date extra pe Wikipedia doar pentru atracții turistice
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

            // SALVĂM în baza de date (sau actualizăm dacă există deja)
            saveRecommendation(dto, city, wikiLink, imageUrl, description);

            // Adăugăm în lista finală ce va fi returnată utilizatorului
            finalResultList.add(new RecommandationDto(
                    dto.id(), dto.name(), dto.englishName(), description, dto.category(),
                    dto.lat(), dto.lon(), wikiLink, imageUrl
            ));
        }

        return finalResultList;
    }

    /**
     * AI-ul generează lista celor mai POPULARE și FAIMOASE locuri din oraș
     */
    private List<RecommandationDto> fetchMostPopularFromAI(String cityName) {
        String prompt = String.format(
                "You are a travel expert. List the MOST POPULAR and FAMOUS places in %s that every tourist MUST visit.\n\n" +
                        "Provide EXACTLY:\n" +
                        "- %d Tourist Attractions (the most famous landmarks, museums, historic sites)\n" +
                        "- %d Hotels (the most famous/popular hotels)\n" +
                        "- %d Restaurants (the most famous/popular restaurants)\n\n" +
                        "IMPORTANT RULES:\n" +
                        "1. Only include REAL, EXISTING places that are WELL-KNOWN and VERIFIED\n" +
                        "2. Use the OFFICIAL LOCAL NAMES (not translated)\n" +
                        "3. For attractions: include UNESCO sites, famous landmarks, main squares, cathedrals, castles, museums\n" +
                        "4. For hotels: include well-known international chains or famous local hotels\n" +
                        "5. For restaurants: include famous local restaurants known for traditional cuisine\n" +
                        "6. Write a 1-2 sentence description for each\n\n" +
                        "Return JSON: {\"recommendations\": [{\"name\": \"exact local name\", \"englishName\": \"English name if different\", \"description\": \"short description\", \"category\": \"Tourist Attraction|Hotel|Restaurant\"}]}",
                cityName, MAX_ATTRACTIONS, MAX_HOTELS, MAX_RESTAURANTS
        );

        return callGroqAI(prompt);
    }

    /**
     * Verifică fiecare loc cu Nominatim pentru a obține coordonate REALE
     * Dacă locul nu există, îl excludem (nu e real)
     */
    private List<RecommandationDto> verifyAndGetCoordinates(List<RecommandationDto> aiPlaces, String cityName) {
        List<RecommandationDto> verified = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        int attractionsCount = 0;
        int hotelsCount = 0;
        int restaurantsCount = 0;

        for (RecommandationDto place : aiPlaces) {
            // Verificăm limitele per categorie
            if ("Tourist Attraction".equals(place.category()) && attractionsCount >= MAX_ATTRACTIONS) continue;
            if ("Hotel".equals(place.category()) && hotelsCount >= MAX_HOTELS) continue;
            if ("Restaurant".equals(place.category()) && restaurantsCount >= MAX_RESTAURANTS) continue;

            // Verificăm duplicatele
            if (addedNames.contains(place.name().toLowerCase())) continue;

            // Obținem coordonatele reale de la Nominatim
            double[] coords = getPlaceCoordinates(place.name(), cityName);

            if (coords != null) {
                verified.add(new RecommandationDto(
                        null, place.name(), place.englishName(), place.description(),
                        place.category(), coords[0], coords[1], null, null
                ));
                addedNames.add(place.name().toLowerCase());

                // Incrementăm contorul pentru categoria respectivă
                if ("Tourist Attraction".equals(place.category())) attractionsCount++;
                else if ("Hotel".equals(place.category())) hotelsCount++;
                else if ("Restaurant".equals(place.category())) restaurantsCount++;
            }
        }

        // Dacă nu avem destule atracții, încercăm să completăm cu o a doua cerere AI
        if (attractionsCount < MAX_ATTRACTIONS) {
            List<RecommandationDto> moreAttractions = fetchAdditionalAttractions(cityName, addedNames, MAX_ATTRACTIONS - attractionsCount);
            for (RecommandationDto attr : moreAttractions) {
                double[] coords = getPlaceCoordinates(attr.name(), cityName);
                if (coords != null && !addedNames.contains(attr.name().toLowerCase())) {
                    verified.add(new RecommandationDto(
                            null, attr.name(), attr.englishName(), attr.description(),
                            "Tourist Attraction", coords[0], coords[1], null, null
                    ));
                    addedNames.add(attr.name().toLowerCase());
                }
            }
        }

        return verified;
    }

    /**
     * Obține coordonatele unui loc folosind Nominatim API
     */
    private double[] getPlaceCoordinates(String placeName, String cityName) {
        try {
            // Prima încercare: numele complet + oraș
            String query = URLEncoder.encode(placeName + ", " + cityName, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + query + "&format=json&limit=1";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode results = objectMapper.readTree(response);

            if (results.size() > 0) {
                double lat = results.get(0).path("lat").asDouble();
                double lon = results.get(0).path("lon").asDouble();
                return new double[]{lat, lon};
            }

            // A doua încercare: doar numele
            query = URLEncoder.encode(placeName, StandardCharsets.UTF_8);
            url = "https://nominatim.openstreetmap.org/search?q=" + query + "&format=json&limit=1";
            response = restTemplate.getForObject(url, String.class);
            results = objectMapper.readTree(response);

            if (results.size() > 0) {
                double lat = results.get(0).path("lat").asDouble();
                double lon = results.get(0).path("lon").asDouble();
                return new double[]{lat, lon};
            }

            // A treia încercare: Photon API
            return getPlaceCoordinatesFromPhoton(placeName, cityName);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback: folosește Photon API pentru coordonate
     */
    private double[] getPlaceCoordinatesFromPhoton(String placeName, String cityName) {
        try {
            String query = URLEncoder.encode(placeName + " " + cityName, StandardCharsets.UTF_8);
            String url = "https://photon.komoot.io/api/?q=" + query + "&limit=1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.path("features").size() > 0) {
                JsonNode coords = root.path("features").get(0).path("geometry").path("coordinates");
                return new double[]{coords.get(1).asDouble(), coords.get(0).asDouble()};
            }
        } catch (Exception e) {}
        return null;
    }

    /**
     * Cere AI-ului mai multe atracții dacă primele nu au fost găsite
     */
    private List<RecommandationDto> fetchAdditionalAttractions(String cityName, Set<String> excludeNames, int count) {
        String excludeList = String.join(", ", excludeNames);
        String prompt = String.format(
                "List %d MORE famous tourist attractions in %s. EXCLUDE these already listed: [%s].\n" +
                        "Include: squares, parks, churches, museums, monuments, historic buildings.\n" +
                        "Use OFFICIAL LOCAL NAMES.\n" +
                        "Return JSON: {\"recommendations\": [{\"name\": \"local name\", \"englishName\": \"English name\", \"description\": \"short description\", \"category\": \"Tourist Attraction\"}]}",
                count + 5, cityName, excludeList
        );
        return callGroqAI(prompt);
    }

    private String extractSimpleCityName(String fullName) {
        if (fullName.contains(",")) return fullName.split(",")[0].trim();
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
                Recommendation rec = existing.get();
                rec.setWikipediaLink(wikiLink);
                rec.setImageUrl(imageUrl);
                rec.setDescription(description);
                recommendationRepository.save(rec);
            }
        } catch (Exception e) {}
    }

    private static class WikiData {
        String url;
        String imageUrl;
        String extract;
        public WikiData(String url, String imageUrl, String extract) {
            this.url = url; this.imageUrl = imageUrl; this.extract = extract;
        }
    }

    private WikiData searchWikipediaData(String name, String city) {
        try {
            String title = performWikiSearch(name + " " + city, city);
            if (title == null) {
                title = performWikiSearch(name, city);
            }

            if (title != null) {
                String detailsUrl = "https://ro.wikipedia.org/w/api.php?action=query&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&prop=pageimages|extracts&pithumbsize=400&exintro=1&explaintext=1&format=json";
                String response = restTemplate.getForObject(detailsUrl, String.class);
                JsonNode pages = objectMapper.readTree(response).path("query").path("pages");

                if (pages.fieldNames().hasNext()) {
                    JsonNode page = pages.get(pages.fieldNames().next());
                    String imageUrl = page.path("thumbnail").path("source").asText(null);
                    String extract = page.path("extract").asText(null);
                    if (extract != null && extract.length() > 300) extract = extract.substring(0, 300) + "...";

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

    private String performWikiSearch(String query, String city) {
        try {
            String url = "https://ro.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&format=json";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode results = objectMapper.readTree(response).path("query").path("search");

            if (results.size() > 0) {
                for (JsonNode res : results) {
                    String title = res.path("title").asText();
                    if (!title.equalsIgnoreCase(city)) return title;
                }
            }
        } catch (Exception e) {}
        return null;
    }

    public double[] getCityCoordinates(String city) {
        try {
            String url = "https://photon.komoot.io/api/?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&limit=1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("features").size() > 0) {
                JsonNode coords = root.path("features").get(0).path("geometry").path("coordinates");
                return new double[]{coords.get(1).asDouble(), coords.get(0).asDouble()};
            }
        } catch (Exception e) {}
        return null;
    }

    private List<RecommandationDto> callGroqAI(String prompt) {
        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.groqApiKey);
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.groqModel);
        requestBody.put("temperature", 0.2);
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
        } catch (Exception e) { return Collections.emptyList(); }
    }

    public String getCityNotification(String cityName) { return "Welcome to " + cityName; }
    
    // MODIFICARE: Folosim AI-ul pentru a genera un mesaj personalizat și interesant
    public String getLiveRecommendation(UserLocationDto location) {
        String prompt = String.format(
            "You are a local travel guide. The user is currently at coordinates (lat: %f, lon: %f) near '%s' in '%s'.\n" +
            "Generate a SHORT, EXCITING, and REAL-TIME notification (max 1 sentence) suggesting a nearby hidden gem, a fun fact about the location, or a quick activity.\n" +
            "Do NOT just say 'Welcome to...'. Be specific and engaging.\n" +
            "Example: 'Did you know the oldest cafe in the city is just around the corner? Check out Cafe Central!'",
            location.latitude(), location.longitude(), location.locationName(), location.city()
        );
        
        try {
            // Reutilizăm logica de apelare Groq, dar adaptată pentru un singur string
            String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(this.groqApiKey);
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", this.groqModel);
            requestBody.put("temperature", 0.7); // Mai creativ
            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode content = objectMapper.readTree(response).path("choices").get(0).path("message").path("content");
            
            return content.asText().replace("\"", "").trim();
        } catch (Exception e) {
            return "Explore the hidden gems around " + location.locationName() + "!";
        }
    }
}
