package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.model.Movie;
import org.springframework.data.domain.Page;

public interface MovieRepository extends JpaRepository<Movie, Integer> {
    Page<Movie> findByCategory(String categoryName);
}
