package com.example.project.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "WatchHistory", 
       uniqueConstraints = { @UniqueConstraint(columnNames = { "userID", "movieID" }) })
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movieID", nullable = false)
    private Movie movie;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime firstWatchedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastWatchedAt;

    public WatchHistory() {
    }

    public WatchHistory(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public LocalDateTime getFirstWatchedAt() { return firstWatchedAt; }
    public void setFirstWatchedAt(LocalDateTime firstWatchedAt) { this.firstWatchedAt = firstWatchedAt; }

    public LocalDateTime getLastWatchedAt() { return lastWatchedAt; }
    public void setLastWatchedAt(LocalDateTime lastWatchedAt) { this.lastWatchedAt = lastWatchedAt; }
}