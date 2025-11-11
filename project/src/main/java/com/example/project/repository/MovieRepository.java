package com.example.project.repository;

import com.example.project.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    
    // Thêm hàm này để kiểm tra xem phim từ TMDB đã tồn tại hay chưa
    Optional<Movie> findByTmdbId(Integer tmdbId);
    List<Movie> findTop20ByOrderByReleaseDateDesc();
}