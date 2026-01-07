package com.example.demo.DTO;

public record RecommandationDto(
        Long id,
        String name,
        String englishName,
        String description,
        String category,
        Double lat,
        Double lon,
        String wikipediaLink,
        String imageUrl // Câmp nou pentru imagine
) {
    // Constructor compact
    public RecommandationDto(String name, String englishName, String description, String category) {
        this(null, name, englishName, description, category, null, null, null, null);
    }
    
    // Constructor pentru utilizare cu coordonate
    public RecommandationDto(Long id, String name, String englishName, String description, String category, Double lat, Double lon) {
        this(id, name, englishName, description, category, lat, lon, null, null);
    }

    // Metodă helper
    public RecommandationDto withCoordinates(Double lat, Double lon) {
        return new RecommandationDto(id, name, englishName, description, category, lat, lon, wikipediaLink, imageUrl);
    }
}
