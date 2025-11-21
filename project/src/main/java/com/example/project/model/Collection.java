// File: src/main/java/com/example/project/model/Collection.java
package com.example.project.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Collection")
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(unique = true, nullable = false)
    private Integer tmdbId;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String name;

    private String posterPath;
    private String backdropPath;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Movie> movies = new ArrayList<>();

    // --- Constructors ---
    public Collection() {}

    public Collection(Integer tmdbId, String name, String posterPath, String backdropPath) {
        this.tmdbId = tmdbId;
        this.name = name;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
    }

    // --- Getters & Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}