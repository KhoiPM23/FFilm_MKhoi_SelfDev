package com.example.project.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

    @Entity
@Table(name = "Season")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int seasonID;

    @NotNull(message = "seasonNumber is required")
    private int seasonNumber;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message="realaseDate is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date releaseDate;

    @ManyToOne
    @JoinColumn(name = "movieID")
    private Movie movie;

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL)
    private List<Episode> episodes = new ArrayList<>();
    public Season() {
    }
    public Season(int seasonID, int seasonNumber, String title, Date releaseDate, Movie movie, List<Episode> episodes) {
        this.seasonID = seasonID;
        this.seasonNumber = seasonNumber;
        this.title = title;
        this.releaseDate = releaseDate;
        this.movie = movie;
        this.episodes = episodes;
    }
    

    public int getSeasonID() {
        return seasonID;
    }

    public void setSeasonID(int seasonID) {
        this.seasonID = seasonID;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(List<Episode> episodes) {
        this.episodes = episodes;
    }

    // Getter/Setter
}
