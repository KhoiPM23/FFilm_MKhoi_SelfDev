package com.example.project.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.model.Category;
import com.example.project.model.Movie;

public interface MovieRepository extends JpaRepository<Movie, Integer> {
    Page<Movie> findByCategoriesContaining(Category category, Pageable pageable);

    Optional<Movie> findByTitle(String title);

}
