package com.example.project.dto;

public class MovieFavorite {
    private Integer id;
    private String title;
    private String posterPath;
    private Integer tmdbId;

    public MovieFavorite(Integer id, String title, String posterPath, Integer tmdbId) {
        this.id = id;
        this.title = title;
        this.posterPath = posterPath;
        this.tmdbId = tmdbId;
    }

    public MovieFavorite() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }
}
