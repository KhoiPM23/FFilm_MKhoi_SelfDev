
package com.example.project.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "Movie")

public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int movieID;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "description is required")
    private String description;

    @NotNull(message = "releaseDate is not null")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date releaseDate;

    @Column(nullable = false)
    private boolean isFree;

    @NotNull(message = "Duration is not null")
    private int duration;

    @NotNull(message = "Rating is not null")
    private float rating;

    @NotBlank(message = "url  is not null")
    private String url;

    // Quan hệ 1-N: Movie -> Season
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Season> seasons = new ArrayList<>();

    // N-N với Person
    @ManyToMany
    @JoinTable(name = "MoviePerson", joinColumns = @JoinColumn(name = "movieID"), inverseJoinColumns = @JoinColumn(name = "personID"))
    private List<Person> persons = new ArrayList<>();

    // N-N với Category
    @ManyToMany
    @JoinTable(name = "MovieCategory", joinColumns = @JoinColumn(name = "movieID"), inverseJoinColumns = @JoinColumn(name = "categoryID"))
    private List<Category> categories = new ArrayList<>();

    // Quan hệ với Review, Comment, Report
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Comment> comments;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Report> reports;

    public Movie() {
    }

    public Movie(int movieID, String title, String description, Date releaseDate, boolean isFree, int duration,
            float rating, String url, List<Season> seasons, List<Person> persons, List<Category> categories,
            List<Review> reviews, List<Comment> comments, List<Report> reports) {
        this.movieID = movieID;
        this.title = title;
        this.description = description;
        this.releaseDate = releaseDate;
        this.isFree = isFree;
        this.duration = duration;
        this.rating = rating;
        this.url = url;
        this.seasons = seasons;
        this.persons = persons;
        this.categories = categories;
        this.reviews = reviews;
        this.comments = comments;
        this.reports = reports;
    }

    public int getMovieID() {
        return movieID;
    }

    public void setMovieID(int movieID) {
        this.movieID = movieID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public boolean isFree() {
        return isFree;
    }

    public void setFree(boolean isFree) {
        this.isFree = isFree;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<Season> getSeasons() {
        return seasons;
    }

    public void setSeasons(List<Season> seasons) {
        this.seasons = seasons;
    }

    public List<Person> getPersons() {
        return persons;
    }

    public void setPersons(List<Person> persons) {
        this.persons = persons;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(List<Review> reviews) {
        this.reviews = reviews;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

}
