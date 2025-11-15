package com.example.project.dto;

public class ReactionRequest {
    private Integer userId;
    private Integer tmdbId;
    private Integer movieID;

    public ReactionRequest(Integer userId, Integer tmdbId) {
        this.userId = userId;
        this.tmdbId = tmdbId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer gettmdbId() {
        return tmdbId;
    }

    public void settmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

}
