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
@Table(name = "Category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int categoryID;
    @NotBlank(message = "name is required")
    private String name;
    private Integer categoryParentID;

    @ManyToMany(mappedBy = "categories")
    private List<Movie> movies = new ArrayList<>();

    // Constructor mặc định
    public Category() {}
    
    
    public Category(int categoryID, String name, Integer categoryParentID, List<Movie> movies) {
        this.categoryID = categoryID;
        this.name = name;
        this.categoryParentID = categoryParentID;
        this.movies = movies;
    }


    // Getter và Setter
    public int getCategoryID() {
        return categoryID;
    }

    public void setCategoryID(int categoryID) {
        this.categoryID = categoryID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCategoryParentID() {
        return categoryParentID;
    }

    public void setCategoryParentID(Integer categoryParentID) {
        this.categoryParentID = categoryParentID;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }
}

