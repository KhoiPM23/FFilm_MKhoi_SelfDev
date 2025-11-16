package com.example.project.repository;

import com.example.project.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param; 
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List; 
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer>, JpaSpecificationExecutor<Movie> {
    
    Optional<Movie> findByTmdbId(Integer tmdbId);

    @Query("SELECT m.tmdbId FROM Movie m WHERE m.tmdbId IN :tmdbIds")
    List<Integer> findTmdbIdsIn(@Param("tmdbIds") List<Integer> tmdbIds);

    // [FIX] Sửa lỗi tìm kiếm Tiếng Việt (case-insensitive) bằng Native Query
    @Query(value = "SELECT * FROM Movie m WHERE UPPER(m.title) LIKE N'%' + UPPER(:title) + '%'", nativeQuery = true)
    List<Movie> findByTitleContainingIgnoreCase(@Param("title") String title);

    /**
     * [THÊM MỚI] 
     * Tìm tất cả các đối tượng Movie có tmdbId nằm trong danh sách.
     */
    List<Movie> findByTmdbIdIn(List<Integer> tmdbIds);

    /**
     * [GIẢI PHÁP 3] Thêm các hàm query DB cho carousel
     */
    // Lấy phim hot (rating cao nhất)
    Page<Movie> findAllByOrderByRatingDesc(Pageable pageable);
    
    // Lấy phim mới (ngày ra mắt mới nhất)
    Page<Movie> findAllByOrderByReleaseDateDesc(Pageable pageable);
    
    // Lấy phim theo TMDB Genre ID (ví dụ: 16 cho Anime)
    Page<Movie> findAllByGenres_TmdbGenreId(Integer tmdbGenreId, Pageable pageable);
}