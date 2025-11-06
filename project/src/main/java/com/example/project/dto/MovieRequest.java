package com.example.project.dto;

import java.util.Date;
import java.util.List;

// DTO này dùng để NHẬN dữ liệu khi Content Manager "Thêm mới" hoặc "Cập nhật"
public class MovieRequest {

    private String title;
    private String description;
    private Date releaseDate;
    private boolean isFree;
    private int duration;
    private float rating;
    private String url; // Link video
    private String posterPath; // Link ảnh poster
    private String backdropPath; // Link ảnh banner
    
    // Chúng ta chỉ nhận ID từ frontend
    private List<Integer> categoryIds;
    private List<Integer> personIds;
    
    // Getters and Setters...
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getReleaseDate() { return releaseDate; }
    public void setReleaseDate(Date releaseDate) { this.releaseDate = releaseDate; }
    public boolean isFree() { return isFree; }
    public void setFree(boolean isFree) { this.isFree = isFree; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }
    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }
    public List<Integer> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<Integer> categoryIds) { this.categoryIds = categoryIds; }
    public List<Integer> getPersonIds() { return personIds; }
    public void setPersonIds(List<Integer> personIds) { this.personIds = personIds; }
}