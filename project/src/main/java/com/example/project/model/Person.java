package com.example.project.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "Person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int personID;

    @Column(unique = true)
    private Integer tmdbId; // ID TMDB để đồng bộ

    @Column(columnDefinition = "NVARCHAR(255)")
    private String fullName;

    @Column(columnDefinition = "NVARCHAR(MAX)") // Tiểu sử có thể rất dài
    private String bio;

    @Temporal(TemporalType.DATE)
    private Date birthday; // Ngày sinh

    @Column(columnDefinition = "NVARCHAR(255)")
    private String placeOfBirth; // Nơi sinh

    private String profilePath; // Link ảnh đại diện

    // Nghề nghiệp chính (VD: Acting, Directing...)
    private String knownForDepartment; 

    // [BỔ SUNG TRƯỜNG BỊ THIẾU]
    private Double popularity; // Chỉ số độ nổi tiếng

    @ManyToMany(mappedBy = "persons")
    @JsonIgnore
    private List<Movie> movies = new ArrayList<>();

    public Person() {}

    // --- GETTERS & SETTERS (Đã bao gồm popularity) ---

    public int getPersonID() { return personID; }
    public void setPersonID(int personID) { this.personID = personID; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Date getBirthday() { return birthday; }
    public void setBirthday(Date birthday) { this.birthday = birthday; }

    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }

    public String getProfilePath() { return profilePath; }
    public void setProfilePath(String profilePath) { this.profilePath = profilePath; }

    public String getKnownForDepartment() { return knownForDepartment; }
    public void setKnownForDepartment(String knownForDepartment) { this.knownForDepartment = knownForDepartment; }

    public Double getPopularity() { return popularity; }
    public void setPopularity(Double popularity) { this.popularity = popularity; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}