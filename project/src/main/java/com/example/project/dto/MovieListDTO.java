package com.example.project.dto;

public class MovieListDTO {
    private int movieID;
    private String title;
    private boolean isFree;
    private String url;

    public MovieListDTO(int movieID, String title, boolean isFree, String url) {
        this.movieID = movieID;
        this.title = title;
        this.isFree = isFree;
        this.url = url;
    }

    public MovieListDTO() {
    }

    public int getMovieID() {
        return movieID;
    }

    public void setMovieID(int movieID) {
        this.movieID = movieID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isFree() {
        return isFree;
    }

    public void setFree(boolean isFree) {
        this.isFree = isFree;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
