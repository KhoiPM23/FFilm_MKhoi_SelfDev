package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person; 
import com.example.project.service.MovieService;
import org.json.JSONArray; 
import org.json.JSONObject; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate; 

import java.net.URLEncoder; 
import java.nio.charset.StandardCharsets; 
import java.util.ArrayList; 
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet; 
import java.util.List; 
import java.util.Map;
import java.util.Set; 
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movie")
public class MovieApiController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private RestTemplate restTemplate;

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    /**
     * [SỬA] API này tìm phim theo TÊN trong DB.
     * Dùng cho Live Suggestion (Trang 1).
     * Endpoint: GET /api/movie/search-db?query=...
     */
    @GetMapping("/search-db")
    public ResponseEntity<List<Map<String, Object>>> liveSearchDb(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        
        // 1. Tìm kiếm như cũ
        List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
        
        // 2. [SỬA] Nâng cấp từng kết quả tìm thấy
        List<Map<String, Object>> mappedResults = dbResults.stream()
            .map(movie -> {
                // Gọi getMoviePartial cho TỪNG phim để đảm bảo nó được nâng cấp
                // (Hàm này sẽ tự lấy trong DB, và nâng cấp nếu là bản "cụt")
                Movie upgradedMovie = movieService.getMoviePartial(movie.getTmdbId());
                // Convert bản đã nâng cấp
                return movieService.convertToMap(upgradedMovie);
            }) 
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(mappedResults);
    }
    
    /**
     * [SỬA] API này kiểm tra phim theo TMDB ID.
     * Dùng cho Live Suggestion (Tất cả các trang).
     * Endpoint: POST /api/movie/check-db
     * Body: [11617, 12345, ...]
     */
    @PostMapping("/check-db")
    public ResponseEntity<Map<Integer, Map<String, Object>>> checkDbForMovies(@RequestBody List<Integer> tmdbIds) {
        try {
            // [SỬA] Không dùng getMoviesByTmdbIds() nữa
            // Thay vào đó, lặp qua các ID và gọi getMoviePartial cho từng cái
            
            Map<Integer, Map<String, Object>> dbMoviesMap = new HashMap<>();
            
            for (Integer tmdbId : tmdbIds) {
                // getMoviePartial sẽ tự động:
                // 1. Tìm trong DB
                // 2. Nâng cấp nếu là bản "cụt"
                // 3. (Nếu không có) Sẽ tự tạo mới bản "vừa" (có duration/country)
                Movie movie = movieService.getMoviePartial(tmdbId); 
                
                if (movie != null) {
                    // Trả về bản đồ đã được convert
                    dbMoviesMap.put(tmdbId, movieService.convertToMap(movie));
                }
            }
            return ResponseEntity.ok(dbMoviesMap);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyMap()); // Trả về rỗng nếu lỗi
        }
    }

    // ... (Các hàm getHoverDetail và getBannerDetail giữ nguyên y như cũ)
    
    @GetMapping("/hover-detail/{id}")
    public ResponseEntity<?> getHoverDetail(@PathVariable("id") int tmdbId) {
        // (Giữ nguyên)
        try {
            Movie movie = movieService.getMoviePartial(tmdbId); 
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

    @GetMapping("/banner-detail/{id}")
    public ResponseEntity<?> getBannerDetail(@PathVariable("id") int tmdbId) {
        // (Giữ nguyên)
        try {
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

    /**
     * [MỚI] API lấy banner detail theo movieID (DB PK)
     */
    @GetMapping("/banner-detail-by-movieid/{movieId}")
    public ResponseEntity<?> getBannerDetailByMovieId(@PathVariable("movieId") int movieID) {
        try {
            // Lấy movie theo movieID
            Movie movie = movieService.getMovieByIdOrSync(movieID);
            if (movie == null || movie.getTmdbId() == null) {
                // Phim tự tạo (không có tmdbId) → Không có trailer/logo
                Map<String, Object> data = new HashMap<>();
                data.put("trailerKey", null);
                data.put("logoPath", null);
                return ResponseEntity.ok(data);
            }
            
            // Lấy trailer/logo từ tmdbId
            int tmdbId = movie.getTmdbId();
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