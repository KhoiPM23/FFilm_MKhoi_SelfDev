package com.example.project.dto;

import java.util.Date;
import java.util.List;

// Thêm các import validation
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PastOrPresent;

public class MovieRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @Size(max = 4000, message = "Mô tả quá dài")
    private String description;

    @NotNull(message = "Ngày phát hành không được để trống")
    @PastOrPresent(message = "Ngày phát hành không được lớn hơn ngày hiện tại")
    private Date releaseDate;

    @NotNull(message = "Trường 'isFree' là bắt buộc")
    private boolean isFree;

    @NotNull(message = "Thời lượng không được trống")
    @Min(value = 0, message = "Thời lượng không được âm")
    private int duration;

    @NotNull(message = "Rating không được trống")
    @Min(value = 0, message = "Rating không được âm")
    @Max(value = 10, message = "Rating không được lớn hơn 10")
    private float rating;

    @NotBlank(message = "URL không được để trống")
    @Size(max = 1000, message = "URL quá dài")
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