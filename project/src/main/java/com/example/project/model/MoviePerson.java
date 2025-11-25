package com.example.project.model;

import jakarta.persistence.*;

@Entity
@Table(name = "Movie_Person") 
@IdClass(MoviePersonId.class)
public class MoviePerson {

    @Id
    @Column(name = "movieID")
    private int movieID;

    @Id
    @Column(name = "personID")
    private int personID;

    // [MỚI] Thêm 2 trường lưu role
    @Column(name = "character_name", columnDefinition = "NVARCHAR(255)")
    private String characterName;

    @Column(name = "job", columnDefinition = "NVARCHAR(255)")
    private String job; // Vd: Director, Acting

    public MoviePerson() {}

    public MoviePerson(int movieID, int personID) {
        this.movieID = movieID;
        this.personID = personID;
    }

    // Getters & Setters
    public int getMovieID() { return movieID; }
    public void setMovieID(int movieID) { this.movieID = movieID; }

    public int getPersonID() { return personID; }
    public void setPersonID(int personID) { this.personID = personID; }

    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }

    public String getJob() { return job; }
    public void setJob(String job) { this.job = job; }
}