package com.example.project.controller;

import com.example.project.dto.MovieRequest;
import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Kịch bản 2: Thêm phim thủ công (sau khi đã có data, vd: tự upload)
     * Endpoint: POST /api/content/movies
     * Body: (Xem MovieRequest.java)
     */
    @PostMapping
    public ResponseEntity<?> createMovie(@RequestBody MovieRequest movieRequest) {
        try {
            Movie createdMovie = movieService.createMovie(movieRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cập nhật phim (sửa banner, metadata, url, v.v.)
     * Endpoint: PUT /api/content/movies/{id}
     * Body: (Xem MovieRequest.java)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable int id, @RequestBody MovieRequest movieRequest) {
        try {
            Movie updatedMovie = movieService.updateMovie(id, movieRequest);
            return ResponseEntity.ok(updatedMovie);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Xóa phim
     * Endpoint: DELETE /api/content/movies/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable int id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); // HTTP 204
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}