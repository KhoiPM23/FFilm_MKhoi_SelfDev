package com.example.project.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
// [FIX] Sửa columnNames từ "tmdbId" thành "movieID" để đúng với tên cột JoinColumn bên dưới
@Table(name = "UserReaction", uniqueConstraints = @UniqueConstraint(columnNames = { "userID", "movieID" }))
public class UserReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reactionID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userID", nullable = false, foreignKey = @ForeignKey(name = "FK_UserReaction_User"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movieID", nullable = false) // Đây là cột thực tế trong DB
    private Movie movie;

    @Column(nullable = false)
    private Boolean isLike;

    @Column(nullable = false, columnDefinition = "DATETIME DEFAULT GETDATE()")
    private LocalDateTime createdAt;

    // Constructors
    public UserReaction() {
        this.createdAt = LocalDateTime.now();
    }

    public UserReaction(User user, Movie movie, Boolean isLike) {
        this.user = user;
        this.movie = movie;
        this.isLike = isLike;
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Integer getReactionID() {
        return reactionID;
    }

    public void setReactionID(Integer reactionID) {
        this.reactionID = reactionID;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public Boolean getIsLike() {
        return isLike;
    }

    public void setIsLike(Boolean isLike) {
        this.isLike = isLike;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}