package com.example.project.controller;

import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.model.Person; 
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movie")
public class MovieApiController {

    @Autowired private MovieService movieService;
    
    // [THAY ĐỔI] Bỏ RestTemplate

    //---- 1. API SEARCH LIVE (Offline - DB Only) ----
    @GetMapping("/search-db")
    public ResponseEntity<List<Map<String, Object>>> liveSearchDb(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 2) return ResponseEntity.ok(List.of());
        
        String cleanQuery = query.trim();
        Map<Integer, Map<String, Object>> resultMap = new LinkedHashMap<>();

        // 1. Tìm theo Tên phim
        List<Movie> dbTitleResults = movieService.searchMoviesByTitle(cleanQuery);
        for (Movie m : dbTitleResults) {
            resultMap.put(m.getMovieID(), movieService.convertToMap(m));
        }
        
        // 2. Tìm theo Tên Người
        List<Person> persons = movieService.searchPersons(cleanQuery);
        for (Person p : persons) {
            for (Movie m : p.getMovies()) {
                if (!resultMap.containsKey(m.getMovieID())) {
                    Map<String, Object> map = movieService.convertToMap(m);
                    String roleInfo = "Diễn viên: " + p.getFullName();
                    if (m.getDirector() != null && m.getDirector().equalsIgnoreCase(p.getFullName())) {
                        roleInfo = "Đạo diễn: " + p.getFullName();
                    }
                    map.put("role_info", roleInfo);
                    resultMap.put(m.getMovieID(), map);
                }
            }
        }
        return ResponseEntity.ok(new ArrayList<>(resultMap.values()));
    }

    //---- 2. API UTILITY (Banner/Hover) - Offline ----
    
    @GetMapping("/hover-detail/{id}")
    public ResponseEntity<?> getHoverDetail(@PathVariable("id") int movieID) {
        try {
            // [FIX] Dùng hàm Service an toàn (Tránh lỗi Lazy khi lấy Genres)
            Map<String, Object> movieMap = movieService.getMovieDetailMap(movieID);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("movie", movieMap);
            responseData.put("trailerKey", movieMap.get("trailerKey")); // Lấy từ map đã gộp
            
            return ResponseEntity.ok(responseData);
        } catch (Exception e) { 
            System.err.println("Hover API Error: " + e.getMessage());
            return ResponseEntity.status(500).body(null); 
        }
    }

    @GetMapping("/banner-detail/{id}")
    public ResponseEntity<?> getBannerDetail(@PathVariable("id") int movieID) {
        try {
            String trailerKey = movieService.findBestTrailerKey(movieID);
            String logoPath = movieService.findBestLogoPath(movieID);
            
            Map<String, Object> data = new HashMap<>();
            data.put("trailerKey", trailerKey);
            data.put("logoPath", logoPath);
            return ResponseEntity.ok(data);
        } catch (Exception e) { return ResponseEntity.status(500).body(null); }
    }

    //---- 3. API HOME CAROUSELS (Offline) ----

    @GetMapping("/home/new")
    public ResponseEntity<List<Map<String, Object>>> getHomeNewMovies() {
        Page<Movie> dbMovies = movieService.getNewMoviesFromDB(20);
        return ResponseEntity.ok(convertPage(dbMovies));
    }

    @GetMapping("/home/anime")
    public ResponseEntity<List<Map<String, Object>>> getHomeAnimeMovies() {
        Page<Movie> dbMovies = movieService.getMoviesByGenreFromDB(16, 20, 0);
        return ResponseEntity.ok(convertPage(dbMovies));
    }

    @GetMapping("/home/kids")
    public ResponseEntity<List<Map<String, Object>>> getHomeKidsMovies() {
        Page<Movie> dbMovies = movieService.getMoviesByGenreFromDB(10751, 20, 0);
        return ResponseEntity.ok(convertPage(dbMovies));
    }

    @GetMapping("/home/action")
    public ResponseEntity<List<Map<String, Object>>> getHomeActionMovies() {
        Page<Movie> dbMovies = movieService.getMoviesByGenreFromDB(28, 20, 0);
        return ResponseEntity.ok(convertPage(dbMovies));
    }
    
    //---- 4. API DETAIL CAROUSELS (Offline) ----
    
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<Map<String, Object>>> getSimilarMovies(@PathVariable("id") int movieID) {
        try {
            Movie movie = movieService.getMovieById(movieID);
            Set<Genre> genres = movie.getGenres();
            if (genres != null && !genres.isEmpty()) {
                // Logic đơn giản: Lấy phim cùng thể loại đầu tiên
                int firstGenreId = genres.iterator().next().getTmdbGenreId();
                Page<Movie> similar = movieService.getMoviesByGenreFromDB(firstGenreId, 10, 0);
                
                // Lọc bỏ chính phim đang xem
                List<Map<String, Object>> list = similar.getContent().stream()
                    .filter(m -> m.getMovieID() != movieID)
                    .map(movieService::convertToMap)
                    .collect(Collectors.toList());
                return ResponseEntity.ok(list);
            }
            return ResponseEntity.ok(new ArrayList<>());
        } catch (Exception e) { return ResponseEntity.ok(new ArrayList<>()); }
    }

    @GetMapping("/{id}/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedMovies(@PathVariable("id") int movieID) {
        Map<String, Object> response = new HashMap<>();
        try {
            Movie movie = movieService.getMovieById(movieID);
            // Gọi hàm Waterfall (đã bao gồm logic 5 lớp và tự điền title/image vào response)
            List<Map<String, Object>> movies = movieService.getRecommendedMoviesWaterfall(movie, response);
            
            response.put("movies", movies);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Fallback an toàn nếu lỗi
            e.printStackTrace();
            response.put("title", "✨ Có Thể Bạn Thích");
            response.put("movies", new ArrayList<>());
            return ResponseEntity.ok(response);
        }
    }
    
    @GetMapping("/{id}/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrendingSidebar() {
        Page<Movie> hot = movieService.getHotMoviesFromDB(10);
        return ResponseEntity.ok(convertPage(hot));
    }

    // Helper convert
    private List<Map<String, Object>> convertPage(Page<Movie> page) {
        return page.getContent().stream()
            .map(movieService::convertToMap)
            .collect(Collectors.toList());
    }
    
    // API Dummy để tránh lỗi JS frontend (check-db, sync-by-tmdbid)
    @PostMapping("/check-db")
    public ResponseEntity<?> dummyCheckDb() { return ResponseEntity.ok(Collections.emptyMap()); }
    
    @GetMapping("/sync-by-tmdbid/{tmdbId}")
    public ResponseEntity<?> dummySync() { return ResponseEntity.notFound().build(); }
}