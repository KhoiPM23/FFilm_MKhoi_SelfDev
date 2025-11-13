package com.example.project.repository;

import com.example.project.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // <-- [THÊM IMPORT]
import org.springframework.data.repository.query.Param; // <-- [THÊM IMPORT]
import org.springframework.stereotype.Repository;

import java.util.List; // <-- [THÊM IMPORT]
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    
    Optional<Movie> findByTmdbId(Integer tmdbId);

    // [BỔ SUNG HÀM NÀY ĐỂ FIX LỖI]
    @Query("SELECT m.tmdbId FROM Movie m WHERE m.tmdbId IN :tmdbIds")
    List<Integer> findTmdbIdsIn(@Param("tmdbIds") List<Integer> tmdbIds);
}