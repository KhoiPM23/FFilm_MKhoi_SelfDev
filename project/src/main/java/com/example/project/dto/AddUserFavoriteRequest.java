package com.example.project.dto;

import java.sql.Date;

//Linh

public class AddUserFavoriteRequest {
    private Integer movieID;
    private Integer UserID;
    private Date createAt;

    public AddUserFavoriteRequest() {

    }

    public AddUserFavoriteRequest(Integer movieID, Integer userID, Date createAt) {
        this.movieID = movieID;
        UserID = userID;
        this.createAt = createAt;
    }

    public Integer getMovieID() {
        return movieID;
    }

    public void setMovieID(Integer movieID) {
        this.movieID = movieID;
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
