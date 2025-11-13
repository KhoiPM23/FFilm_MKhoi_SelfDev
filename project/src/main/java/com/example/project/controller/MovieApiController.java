package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/movie")
public class MovieApiController {

    @Autowired
    private MovieService movieService;

    // Trong file MovieApiController.java

    /**
     * [G43] Sửa lỗi: Hover Card phải gọi hàm LAZY
     */
    @GetMapping("/hover-detail/{id}")
    public ResponseEntity<?> getHoverDetail(@PathVariable("id") int tmdbId) {
        try {
            // [G43] SỬA LỖI:
            // Movie movie = movieService.getMovieOrSync(tmdbId); // LỖI (Eager)
            Movie movie = movieService.getMoviePartial(tmdbId); // ĐÚNG (Lazy)
            
            if (movie == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> movieData = movieService.convertToMap(movie);

            String trailerKey = movieService.findBestTrailerKey(tmdbId);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("movie", movieData);
            responseData.put("trailerKey", trailerKey);

            return ResponseEntity.ok(responseData);
            
        } catch (Exception e) {
            System.err.println("Lỗi API getHoverDetail cho ID " + tmdbId + ": " + e.getMessage());
            return ResponseEntity.status(500).body("Lỗi server");
        }
    }

    /**
     * [G8] API nội bộ MỚI cho Mini-Carousel (Sửa lỗi video banner)
     */
    @GetMapping("/banner-detail/{id}")
    public ResponseEntity<?> getBannerDetail(@PathVariable("id") int tmdbId) {
        try {
            // Hàm này không cần lưu DB, chỉ cần lấy 2 thông tin
            String trailerKey = movieService.findBestTrailerKey(tmdbId);
            String logoPath = movieService.findBestLogoPath(tmdbId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("trailerKey", trailerKey);
            data.put("logoPath", logoPath);
            
            return ResponseEntity.ok(data);
        } catch (Exception e) {
             return ResponseEntity.status(500).body("Lỗi server");
        }
    }
}