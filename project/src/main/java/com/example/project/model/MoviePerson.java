// src/main/java/com/example/project/model/MoviePerson.java
package com.example.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "MoviePerson")
@IdClass(MoviePersonId.class) // Dùng composite key
public class MoviePerson {

    @Id
    @Column(name = "movieID")
    private int movieID;

    @Id
    @Column(name = "personID")
    private int personID;

    // Constructors
    public MoviePerson() {
    }

    public MoviePerson(int movieID, int personID) {
        this.movieID = movieID;
        this.personID = personID;
    }

    // Getters & Setters
    public int getMovieID() {
        return movieID;
    }

    public void setMovieID(int movieID) {
        this.movieID = movieID;
    }

    public int getPersonID() {
        return personID;
    }

    public void setPersonID(int personID) {
        this.personID = personID;
    }
}