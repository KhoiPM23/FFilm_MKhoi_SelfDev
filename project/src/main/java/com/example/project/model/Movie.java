package com.example.project.model;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "Movie", indexes = {
    @Index(name = "idx_movie_tmdb_id", columnList = "tmdbId"),
    @Index(name = "idx_movie_rating", columnList = "rating"),
    @Index(name = "idx_movie_popularity", columnList = "popularity"),
    @Index(name = "idx_movie_release_date", columnList = "releaseDate")
})
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int movieID;

    @Column(unique = true, nullable = false)
    private Integer tmdbId;

    @NotBlank(message = "Title is required")
    @Column(columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date releaseDate;
    
    private int duration; 
    private float rating; 
    
    // --- CÁC TRƯỜNG CACHE ---
    private Double popularity; 
    private Integer voteCount; 

    @Column(columnDefinition = "NVARCHAR(100)")
    private String trailerKey; 

    @Column(columnDefinition = "NVARCHAR(500)")
    private String logoPath;   

    @Column(columnDefinition = "NVARCHAR(500)")
    private String posterPath; 

    @Column(columnDefinition = "NVARCHAR(500)")
    private String backdropPath;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String director; 

    @Column(columnDefinition = "NVARCHAR(255)")
    private String country; 

    @Column(columnDefinition = "NVARCHAR(100)")
    private String language; 

    // [MỚI] Cột lưu Content Rating (T13, T16, T18...)
    @Column(columnDefinition = "NVARCHAR(10)")
    private String contentRating; 

    private Long budget;
    private Long revenue;
    private boolean isFree = false;
    
    @Column(length = 1000)
    private String url = "CHƯA CẬP NHẬT";

    // --- QUAN HỆ ---
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "collectionID")
    private Collection collection;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "Movie_Company",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "companyID")
    )
    @JsonIgnore
    private Set<ProductionCompany> productionCompanies = new HashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Season> seasons = new ArrayList<>(); 

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "Movie_Person",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "personID")
    )
    @JsonIgnore
    private Set<Person> persons = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "Movie_Genre",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "genreID")
    )
    @JsonIgnore
    private Set<Genre> genres = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "MovieCategory",
        joinColumns = @JoinColumn(name = "movieID"),
        inverseJoinColumns = @JoinColumn(name = "categoryID")
    )
    @JsonIgnore
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL) @JsonIgnore private List<Review> reviews;
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL) @JsonIgnore private List<Comment> comments;
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL) @JsonIgnore private List<Report> reports;
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true) @JsonIgnore private List<WatchHistory> watchHistories = new ArrayList<>();

    public Movie() {}

    // --- GETTERS & SETTERS ---
    public int getMovieID() { return movieID; }
    public void setMovieID(int movieID) { this.movieID = movieID; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getContentRating() { return contentRating; }
    public void setContentRating(String contentRating) { this.contentRating = contentRating; }

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
    public Double getPopularity() { return popularity; }
    public void setPopularity(Double popularity) { this.popularity = popularity; }
    public Integer getVoteCount() { return voteCount; }
    public void setVoteCount(Integer voteCount) { this.voteCount = voteCount; }
    public String getTrailerKey() { return trailerKey; }
    public void setTrailerKey(String trailerKey) { this.trailerKey = trailerKey; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public Long getRevenue() { return revenue; }
    public void setRevenue(Long revenue) { this.revenue = revenue; }
    public boolean isFree() { return isFree; }
    public void setFree(boolean isFree) { this.isFree = isFree; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
    public Set<ProductionCompany> getProductionCompanies() { return productionCompanies; }
    public void setProductionCompanies(Set<ProductionCompany> productionCompanies) { this.productionCompanies = productionCompanies; }
    public List<Season> getSeasons() { return seasons; }
    public void setSeasons(List<Season> seasons) { this.seasons = seasons; }
    public Set<Person> getPersons() { return persons; }
    public void setPersons(Set<Person> persons) { this.persons = persons; }
    public Set<Genre> getGenres() { return genres; }
    public void setGenres(Set<Genre> genres) { this.genres = genres; }
    public Set<Category> getCategories() { return categories; }
    public void setCategories(Set<Category> categories) { this.categories = categories; }
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
    public List<Report> getReports() { return reports; }
    public void setReports(List<Report> reports) { this.reports = reports; }
    public List<WatchHistory> getWatchHistories() { return watchHistories; }
    public void setWatchHistories(List<WatchHistory> watchHistories) { this.watchHistories = watchHistories; }
}