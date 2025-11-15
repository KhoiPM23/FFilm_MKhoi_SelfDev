package com.example.project.repository;

import com.example.project.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param; 
import org.springframework.stereotype.Repository;

import java.util.List; 
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    
    Optional<Movie> findByTmdbId(Integer tmdbId);

    @Query("SELECT m.tmdbId FROM Movie m WHERE m.tmdbId IN :tmdbIds")
    List<Integer> findTmdbIdsIn(@Param("tmdbIds") List<Integer> tmdbIds);

    List<Movie> findByTitleContainingIgnoreCase(String title);

    /**
     * [THÊM MỚI] 
     * Tìm tất cả các đối tượng Movie có tmdbId nằm trong danh sách.
     */
    List<Movie> findByTmdbIdIn(List<Integer> tmdbIds);
}