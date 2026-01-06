package com.example.demo.DTO;

public record RecommandationDto(
        Long id,
        String name,
        String localName, // Numele în limba locală
        String description,
        String category
) {
}
