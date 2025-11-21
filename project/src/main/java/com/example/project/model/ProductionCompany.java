// File: src/main/java/com/example/project/model/ProductionCompany.java
package com.example.project.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ProductionCompany")
public class ProductionCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(unique = true, nullable = false)
    private Integer tmdbId; // ID gốc trên TMDB (để tránh trùng)

    @Column(columnDefinition = "NVARCHAR(255)")
    private String name;

    private String logoPath;
    
    @Column(columnDefinition = "NVARCHAR(100)")
    private String originCountry;

    @ManyToMany(mappedBy = "productionCompanies")
    @JsonIgnore
    private Set<Movie> movies = new HashSet<>();

    // --- Constructors ---
    public ProductionCompany() {}

    public ProductionCompany(Integer tmdbId, String name, String logoPath, String originCountry) {
        this.tmdbId = tmdbId;
        this.name = name;
        this.logoPath = logoPath;
        this.originCountry = originCountry;
    }

    // --- Getters & Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }

    public Set<Movie> getMovies() { return movies; }
    public void setMovies(Set<Movie> movies) { this.movies = movies; }
}