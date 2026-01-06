package com.example.demo.DTO;

public record RecommandationDto(
        Long id,
        String name,
        String englishName,
        String description,
        String category,
        Double lat,  // Coordonate validate de backend
        Double lon   // Coordonate validate de backend
) {
    // Constructor compact pentru a permite crearea fără ID/coordonate inițiale
    public RecommandationDto(String name, String englishName, String description, String category) {
        this(null, name, englishName, description, category, null, null);
    }
    
    // Metodă helper pentru a adăuga coordonate
    public RecommandationDto withCoordinates(Double lat, Double lon) {
        return new RecommandationDto(id, name, englishName, description, category, lat, lon);
    }
}
