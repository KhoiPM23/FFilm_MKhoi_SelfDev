package com.example.project.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "Episode")
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int episodeID;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "videoUrl is required")
    private String videoUrl;

    @NotNull(message = "releaseDate is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date releaseDate;

    @ManyToOne
    @JoinColumn(name = "seasonID")
    private Season season;

    public Episode() {
    }

    // Getter/Setter

    public Episode(int episodeID, String title, String videoUrl, Date releaseDate, Season season) {
        this.episodeID = episodeID;
        this.title = title;
        this.videoUrl = videoUrl;
        this.releaseDate = releaseDate;
        this.season = season;
    }

    public Season getSeason() {
        return season;
    }

    public int getEpisodeID() {
        return episodeID;
    }

    public void setEpisodeID(int episodeID) {
        this.episodeID = episodeID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public void setSeason(Season season) {
        this.season = season;
    }

}
