package com.example.project.dto;

public class ReactionRequest {
    private Integer userId;
    private Integer movieID;

    public ReactionRequest(Integer userId, Integer movieID) {
        this.userId = userId;
        this.movieID = movieID;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getMovieID() {
        return movieID;
    }

    public void setMovieID(Integer movieID) {
        this.movieID = movieID;
    }

}
