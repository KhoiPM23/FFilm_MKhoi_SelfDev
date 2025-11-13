package com.example.project.controller;

import com.example.project.dto.MovieRequest;
import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import jakarta.validation.Valid; // <-- THÊM IMPORT NÀY
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content/movies") // API dành riêng cho Content Manager
@CrossOrigin(origins = "*") // Cho phép gọi từ bên ngoài
public class ContentMovieController {

    @Autowired
    private MovieService movieService;

    /**
     * Lấy tất cả phim
     * Endpoint: GET /api/content/movies
     */
    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.getAllMovies());
    }

    /**
     * Lấy 1 phim bằng ID
     * Endpoint: GET /api/content/movies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable int id) {
        try {
            Movie movie = movieService.getMovieById(id);
            return ResponseEntity.ok(movie);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Kịch bản 1: Import phim từ TMDB
     * Endpoint: POST /api/content/movies/import/{tmdbId}
     */
    @PostMapping("/import/{tmdbId}")
    public ResponseEntity<?> importMovie(@PathVariable int tmdbId) {
        try {
            Movie importedMovie = movieService.importFromTmdb(tmdbId);
            return ResponseEntity.status(HttpStatus.CREATED).body(importedMovie);
        } catch (Exception e) {
            // Lỗi RuntimeException từ service (vd: Phim đã tồn tại) sẽ được
            // GlobalExceptionHandler xử lý
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Kịch bản 2: Thêm phim thủ công
     * Endpoint: POST /api/content/movies
     * Body: (Xem MovieRequest.java)
     */
    @PostMapping
    public ResponseEntity<?> createMovie(@Valid @RequestBody MovieRequest movieRequest) {
        // Annotation @Valid sẽ tự động kích hoạt validation
        // Nếu thất bại, GlobalExceptionHandler sẽ bắt và trả về lỗi 400
        try {
            Movie createdMovie = movieService.createMovie(movieRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Cập nhật phim (sửa banner, metadata, url, v.v.)
     * Endpoint: PUT /api/content/movies/{id}
     * Body: (Xem MovieRequest.java)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable int id, @Valid @RequestBody MovieRequest movieRequest) {
        // @Valid cũng được áp dụng cho update
        try {
            Movie updatedMovie = movieService.updateMovie(id, movieRequest);
            return ResponseEntity.ok(updatedMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable int id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); // HTTP 204
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}