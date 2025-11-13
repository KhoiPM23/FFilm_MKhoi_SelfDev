package com.example.project.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "Genre")
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int genreID;

    @Column(unique = true, nullable = false)
    private Integer tmdbGenreId; // ID chuẩn từ TMDB (VD: 28)

    @Column(columnDefinition = "NVARCHAR(100)", nullable = false)
    private String name; // Tên (VD: "Hành động")

    @ManyToMany(mappedBy = "genres")
    @JsonIgnore
    private List<Movie> movies = new ArrayList<>();

    public Genre() {}

    public Genre(Integer tmdbGenreId, String name) {
        this.tmdbGenreId = tmdbGenreId;
        this.name = name;
    }

    // --- Getters & Setters ---
    public int getGenreID() { return genreID; }
    public void setGenreID(int genreID) { this.genreID = genreID; }
    public Integer getTmdbGenreId() { return tmdbGenreId; }
    public void setTmdbGenreId(Integer tmdbGenreId) { this.tmdbGenreId = tmdbGenreId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}