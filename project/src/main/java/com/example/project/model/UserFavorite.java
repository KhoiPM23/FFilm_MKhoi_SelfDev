package com.example.project.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "UserFavorite")
@IdClass(UserFavoriteId.class)
public class UserFavorite {

    @Id
    private int movieID;

    @Id
    private int userID;
    @NotBlank(message = "type is not null")
    private String type;

    @NotNull(message = "createAt is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
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
