package com.example.demo.DTO;

public record UserLocationDto(
        String city,
        String locationName, // Ex: "Piața Unirii" sau "Strada X"
        Double latitude,
        Double longitude
) {
}
