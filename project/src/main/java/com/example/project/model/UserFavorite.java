package com.example.project.model;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "UserFavorite")
@IdClass(UserFavoriteId.class)
public class UserFavorite {

    @Id
    private int movieID;

    @Id
    private int userID;

    private String type;
    private Date createAt;
    public UserFavorite() {
    }
    public UserFavorite(int movieID, int userID, String type, Date createAt) {
        this.movieID = movieID;
        this.userID = userID;
        this.type = type;
        this.createAt = createAt;
    }
    public int getMovieID() {
        return movieID;
    }
    public void setMovieID(int movieID) {
        this.movieID = movieID;
    }
    public int getUserID() {
        return userID;
    }
    public void setUserID(int userID) {
        this.userID = userID;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public Date getCreateAt() {
        return createAt;
    }
    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }
}
