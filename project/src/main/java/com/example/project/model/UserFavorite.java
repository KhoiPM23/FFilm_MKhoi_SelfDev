package com.example.project.model;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "UserFavorite")
@IdClass(UserFavoriteId.class)
public class UserFavorite {

    @Id
    @Column(name = "movieID")
    private int movieID;

    @Id
    @Column(name = "userID")
    private int userID;

    @NotNull(message = "createAt is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date createAt;

    // --- [PHẦN BỔ SUNG ĐỂ FIX LỖI] ---
    // Mapping quan hệ để lấy được Object Movie và User từ ID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movieID", insertable = false, updatable = false) 
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userID", insertable = false, updatable = false)
    private User user;
    
    // ---------------------------------

    public UserFavorite() {
    }

    public UserFavorite(int movieID, int userID, Date createAt) {
        this.movieID = movieID;
        this.userID = userID;
        this.createAt = createAt;
    }

    // --- Getters & Setters ---

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

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    // [QUAN TRỌNG] Getter cho Object Movie (Giải quyết lỗi fav.getMovie())
    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}