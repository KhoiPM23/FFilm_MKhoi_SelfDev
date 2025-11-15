package com.example.project.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "Movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int movieID;

    @Column(unique = true, nullable = true)
    private Integer tmdbId; // ID để đồng bộ với TMDB

    @NotBlank(message = "title is required")
    @Column(columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)") // Cho phép mô tả dài
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date releaseDate;
    
    private int duration; // Thời lượng (phút)
    private float rating; // Điểm đánh giá
    
    @Column(columnDefinition = "NVARCHAR(500)")
    private String posterPath; // Chỉ lưu path (vd: /abc.jpg)

    @Column(columnDefinition = "NVARCHAR(500)")
    private String backdropPath; // Chỉ lưu path (vd: /xyz.jpg)

    // === CÁC TRƯỜNG BỔ SUNG CHO MOVIE DETAIL ===
    @Column(columnDefinition = "NVARCHAR(255)")
    private String director; // Tên đạo diễn

    @Column(columnDefinition = "NVARCHAR(255)")
    private String country; // Quốc gia sản xuất

    @Column(columnDefinition = "NVARCHAR(100)")
    private String language; // Ngôn ngữ gốc

    private Long budget;
    private Long revenue;
    // ==========================================

    // Trường nội bộ của bạn
    private boolean isFree = false;
    @Column(length = 1000)
    private String url = "CHƯA CẬP NHẬT";

    // --- CÁC MỐI QUAN HỆ ---
    
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Season> seasons = new ArrayList<>();

    // Quan hệ với Diễn viên/Đạo diễn (Nhiều - Nhiều)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "Movie_Person",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "personID")
    )
    @JsonIgnore
    private List<Person> persons = new ArrayList<>();

    // Quan hệ với Thể loại (Nhiều - Nhiều) - Dùng để đồng bộ TMDB
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "Movie_Genre",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "genreID")
    )
    @JsonIgnore
    private List<Genre> genres = new ArrayList<>();

    // Quan hệ với Category (Nhiều - Nhiều) - Dùng cho nội bộ của bạn
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "MovieCategory",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "categoryID")
    )
    @JsonIgnore
    private List<Category> categories = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Review> reviews;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Comment> comments;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Report> reports;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore 
    private List<WatchHistory> watchHistories = new ArrayList<>();

    public Movie() {}

    // --- GETTERS & SETTERS (ĐẦY ĐỦ) ---

    public int getMovieID() { return movieID; }
    public void setMovieID(int movieID) { this.movieID = movieID; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getReleaseDate() { return releaseDate; }
    public void setReleaseDate(Date releaseDate) { this.releaseDate = releaseDate; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }

    public Long getRevenue() { return revenue; }
    public void setRevenue(Long revenue) { this.revenue = revenue; }

    public boolean isFree() { return isFree; }
    public void setFree(boolean isFree) { this.isFree = isFree; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<Season> getSeasons() { return seasons; }
    public void setSeasons(List<Season> seasons) { this.seasons = seasons; }

    public List<Person> getPersons() { return persons; }
    public void setPersons(List<Person> persons) { this.persons = persons; }

    public List<Genre> getGenres() { return genres; }
    public void setGenres(List<Genre> genres) { this.genres = genres; }
    
    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<Report> getReports() { return reports; }
    public void setReports(List<Report> reports) { this.reports = reports; }

    public List<WatchHistory> getWatchHistories() {
        return watchHistories;
    }

    public void setWatchHistories(List<WatchHistory> watchHistories) {
        this.watchHistories = watchHistories;
    }
}