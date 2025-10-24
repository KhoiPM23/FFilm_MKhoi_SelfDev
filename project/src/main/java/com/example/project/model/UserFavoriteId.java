package com.example.project.model;
import java.io.Serializable;

public class UserFavoriteId implements Serializable {
    private int movieID;
    private int userID;
    
    public UserFavoriteId() {
    }
    public UserFavoriteId(int movieID, int userID) {
        this.movieID = movieID;
        this.userID = userID;
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

    
}
