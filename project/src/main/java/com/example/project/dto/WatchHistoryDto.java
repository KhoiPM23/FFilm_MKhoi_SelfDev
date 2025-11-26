package com.example.project.dto;

import java.time.LocalDateTime;

import com.example.project.model.WatchHistory;

public class WatchHistoryDto {

    private int movieId;
    private String movieTitle;
    private String moviePosterPath;
    private LocalDateTime lastWatchedAt;
    
    // Thêm URL để click vào xem lại được
    private String movieUrl; 
    private Integer duration;    // Tổng thời lượng phim (phút)
    private Double currentTime;  // Thời gian đã xem (giây)

    public WatchHistoryDto(WatchHistory history) {
        this.movieId = history.getMovie().getMovieID();
        this.movieTitle = history.getMovie().getTitle();
        this.moviePosterPath = history.getMovie().getPosterPath();
        this.lastWatchedAt = history.getLastWatchedAt();
        this.movieUrl = "/movie/player/" + history.getMovie().getMovieID();
        this.duration = history.getMovie().getDuration(); // Lấy từ bảng Movie
        this.currentTime = history.getCurrentTime();      // Lấy từ bảng WatchHistory
    }

    // Getters and Setters
    public int getMovieId() { return movieId; }
    public void setMovieId(int movieId) { this.movieId = movieId; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getMoviePosterPath() { return moviePosterPath; }
    public void setMoviePosterPath(String moviePosterPath) { this.moviePosterPath = moviePosterPath; }

    public LocalDateTime getLastWatchedAt() { return lastWatchedAt; }
    public void setLastWatchedAt(LocalDateTime lastWatchedAt) { this.lastWatchedAt = lastWatchedAt; }

    public String getMovieUrl() { return movieUrl; }
    public void setMovieUrl(String movieUrl) { this.movieUrl = movieUrl; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Double getCurrentTime() { return currentTime; }
    public void setCurrentTime(Double currentTime) { this.currentTime = currentTime; }
}