
package com.example.project.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "Person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int personID;

    @NotBlank(message = "fullname  is not null")
    private String fullName;

    @NotBlank(message = "bio  is not null")
    private String bio;

    @NotBlank(message = "type  is not null")
    private String type;
    @ManyToMany(mappedBy = "persons")
    private List<Movie> movies = new ArrayList<>();

    public Person() {
    }

    public Person(String fullName, String bio, String type, List<Movie> movies) {
        this.fullName = fullName;
        this.bio = bio;
        this.type = type;
        this.movies = movies;
    }

    public int getPersonID() {
        return personID;
    }

    public void setPersonID(int personID) {
        this.personID = personID;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }

}
