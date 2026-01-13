package com.example.demo.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @Column(length = 1000)
    private String description;
    
    private String category;
    private String wikipediaLink;
    private String imageUrl; // Câmp nou
    private Double latitude;
    private Double longitude;

    @ManyToOne
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    public Recommendation() {}

    public Recommendation(String name, String description, String category, String wikipediaLink, String imageUrl, Double latitude, Double longitude, City city) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.wikipediaLink = wikipediaLink;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getWikipediaLink() { return wikipediaLink; }
    public void setWikipediaLink(String wikipediaLink) { this.wikipediaLink = wikipediaLink; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }
}
