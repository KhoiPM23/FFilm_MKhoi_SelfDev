package com.example.project.dto;

import java.sql.Date;

public class AddUserFavoriteRequest {
    private Integer getTmdbId;
    private Integer UserID;
    private Date createAt;

    public AddUserFavoriteRequest() {

    }

    public AddUserFavoriteRequest(Integer getTmdbId, Integer userID, Date createAt) {
        this.getTmdbId = getTmdbId;
        UserID = userID;
        this.createAt = createAt;
    }

    public Integer getTmdbId() {
        return getTmdbId;
    }

    public void setMovieID(Integer getTmdbId) {
        this.getTmdbId = getTmdbId;
    }

    public Integer getUserID() {
        return UserID;
    }

    public void setUserID(Integer userID) {
        UserID = userID;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

}
