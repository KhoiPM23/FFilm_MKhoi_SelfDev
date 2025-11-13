package com.example.project.repository;

import com.example.project.model.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Integer> {
    
    // Tìm theo TMDB ID
    Optional<Genre> findByTmdbGenreId(Integer tmdbGenreId);

    // [G7] HÀM MỚI QUAN TRỌNG:
    // Tự động tìm tất cả Genre theo danh sách ID
    List<Genre> findByTmdbGenreIdIn(List<Integer> tmdbGenreIds);
}