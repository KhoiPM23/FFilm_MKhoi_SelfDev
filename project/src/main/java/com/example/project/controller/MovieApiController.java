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
        
        // 2. [SỬA LỖI - FIX BUG 4]
        // Không gọi getMoviePartial (gây ghi đè). 
        // Chỉ convert dữ liệu đã có trong DB.
        List<Map<String, Object>> mappedResults = dbResults.stream()
            .map(movie -> movieService.convertToMap(movie)) 
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(mappedResults);
    }
    
    /**
     * [SỬA] API này kiểm tra phim theo TMDB ID.
     * Dùng cho Live Suggestion (Tất cả các trang).
     * Endpoint: POST /api/movie/check-db
     * Body: [11617, 12345, ...]
     * * [ĐÃ TỐI ƯU] Sử dụng getMoviesByTmdbIds (1 truy vấn) thay vì N+1
     */
    @PostMapping("/check-db")
    public ResponseEntity<Map<Integer, Map<String, Object>>> checkDbForMovies(@RequestBody List<Integer> tmdbIds) {
        try {
            // Gọi thẳng hàm service đã được tối ưu để lấy Map<tmdbId, Map<String, Object>>
            Map<Integer, Map<String, Object>> dbMoviesMap = movieService.getMoviesByTmdbIds(tmdbIds);
            
            // [LOGIC BỔ SUNG]
            // Những ID nào không có trong DB?
            List<Integer> missingIds = new ArrayList<>();
            for (Integer tmdbId : tmdbIds) {
                if (!dbMoviesMap.containsKey(tmdbId)) {
                    missingIds.add(tmdbId);
                }
            }

            // === SỬA LỖI - FIX BUG 4 ===
            // Đối với những ID bị thiếu, gọi syncMovieFromList (Lazy)
            // thay vì getMoviePartial (Eager)
            for (Integer tmdbId : missingIds) {
                try {
                    // Gọi API chi tiết 1 lần để lấy JSON
                    String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
                    String resp = restTemplate.getForObject(url, String.class);
                    
                    if (resp != null) {
                        // Dùng hàm Lazy (an toàn) để TẠO MỚI
                        Movie movie = movieService.syncMovieFromList(new JSONObject(resp));
                        if (movie != null) {
                            dbMoviesMap.put(tmdbId, movieService.convertToMap(movie));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo lazy-load cho ID (check-db): " + tmdbId + " - " + e.getMessage());
                }
            }
            // ===========================
            
            return ResponseEntity.ok(dbMoviesMap);
            
        } catch (Exception e) {
            e.printStackTrace();
            // Trả về rỗng nếu có lỗi nghiêm trọng
            return ResponseEntity.ok(Collections.emptyMap()); 
        }
    }

    // ... (Các hàm getHoverDetail và getBannerDetail giữ nguyên y như cũ)
    
    /**
     * [SỬA LỖI] API này nhận movieID (PK) từ hover card
     * Endpoint: GET /api/movie/hover-detail/{id}
     */
    @GetMapping("/hover-detail/{id}")
    public ResponseEntity<?> getHoverDetail(@PathVariable("id") int movieID) { // <-- Sửa tên biến
        try {
            // 1. Lấy movie bằng PK (đã bao gồm nâng cấp "vừa")
            Movie movie = movieService.getMovieByIdOrSync(movieID); 
            if (movie == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 2. Convert sang Map
            Map<String, Object> movieData = movieService.convertToMap(movie);
            
            // 3. Lấy trailer key (nếu có)
            String trailerKey = null;
            if (movie.getTmdbId() != null) {
                // Gọi hàm đã sửa, truyền movieID (PK)
                trailerKey = movieService.findBestTrailerKey(movie.getMovieID());
            }

            // 4. Trả về
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("movie", movieData);
            responseData.put("trailerKey", trailerKey);
            return ResponseEntity.ok(responseData);
            
        } catch (Exception e) {
            System.err.println("Lỗi API getHoverDetail cho movieID " + movieID + ": " + e.getMessage());
            return ResponseEntity.status(500).body("Lỗi server");
        }
    }

    /**
     * [SỬA LỖI] API này nhận movieID (PK) từ banner
     * Endpoint: GET /api/movie/banner-detail/{id}
     */
    @GetMapping("/banner-detail/{id}")
    public ResponseEntity<?> getBannerDetail(@PathVariable("id") int movieID) { // <-- Sửa tên biến
        try {
            // Gọi các hàm đã sửa, truyền movieID (PK)
            String trailerKey = movieService.findBestTrailerKey(movieID);
            String logoPath = movieService.findBestLogoPath(movieID);
            
            Map<String, Object> data = new HashMap<>();
            data.put("trailerKey", trailerKey);
            data.put("logoPath", logoPath);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
             System.err.println("Lỗi API getBannerDetail cho movieID " + movieID + ": " + e.getMessage());
             return ResponseEntity.status(500).body("Lỗi server");
        }
    }

    // [GIẢI PHÁP 2] API MỚI CHO TẢI BẤT ĐỒNG BỘ TRANG CHI TIẾT

    /**
     * API tải Trending (dùng cho sidebar)
     * Endpoint: GET /api/movie/{id}/trending
     */
    @GetMapping("/{id}/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrendingSidebar(@PathVariable("id") int movieID) {
        // movieID ở đây không thực sự cần, nhưng để API nhất quán
        return ResponseEntity.ok(loadTrendingSidebar());
    }

    /**
     * API tải Similar (Phim tương tự)
     * Endpoint: GET /api/movie/{id}/similar
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<Map<String, Object>>> getSimilarMovies(@PathVariable("id") int movieID) {
        Movie movie = movieService.getMovieById(movieID); // Lấy movie bằng PK
        if (movie == null || movie.getTmdbId() == null) {
            return ResponseEntity.ok(new ArrayList<>()); // Trả rỗng nếu là phim tự tạo
        }
        return ResponseEntity.ok(loadSimilarMovies(String.valueOf(movie.getTmdbId())));
    }

    /**
     * API tải Recommended (Phim đề xuất / Collection)
     * Endpoint: GET /api/movie/{id}/recommended
     */
    @GetMapping("/{id}/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedMovies(@PathVariable("id") int movieID) {
        Movie movie = movieService.getMovieById(movieID); // Lấy movie bằng PK
        if (movie == null || movie.getTmdbId() == null) {
            // Phim tự tạo
            return ResponseEntity.ok(Map.of("title", "Phim Khác", "movies", new ArrayList<>()));
        }
        
        Integer tmdbId = movie.getTmdbId();
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> movies = loadRecommendedMovies(String.valueOf(tmdbId), tmdbId, response);
        
        response.put("movies", movies);
        if (!response.containsKey("title")) {
            response.put("title", "✨ Có Thể Bạn Thích");
        }
        return ResponseEntity.ok(response);
    }

    // --- CÁC HÀM HELPER (COPY TỪ MOVIEDETAILCONTROLLER VÀ SỬA LỖI) ---
    
    // (Helper 1)
    private List<Map<String, Object>> loadTrendingSidebar() {
        // [FIX VĐ 6] Thêm &include_adult=false
        String url = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
        Map<String, Object> data = movieService.loadAndSyncPaginatedMovies(url, 10);
        return (List<Map<String, Object>>) data.get("movies");
    }

    // (Helper 2)
    private List<Map<String, Object>> loadSimilarMovies(String tmdbId) { // Đã là tmdbId
        // [FIX VĐ 6] Thêm &include_adult=false
        String url = BASE_URL + "/movie/" + tmdbId + "/similar?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
        Map<String, Object> data = movieService.loadAndSyncPaginatedMovies(url, 10);
        return (List<Map<String, Object>>) data.get("movies");
    }

    // (Helper 3)
    private List<Map<String, Object>> loadRecommendedMovies(String tmdbIdStr, int tmdbId, Map<String, Object> response) { // Sửa Model thành Map
        
        Set<Integer> addedMovieIds = new HashSet<>();
        List<Map<String, Object>> finalRecommendations = new ArrayList<>();
        addedMovieIds.add(tmdbId); 

        try {
            // [FIX VĐ 6] Thêm &include_adult=false
            String detailUrl = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
            String detailResp = restTemplate.getForObject(detailUrl, String.class);
            JSONObject movieJson = new JSONObject(detailResp);
            JSONObject collection = movieJson.optJSONObject("belongs_to_collection");
            
            if (collection != null) {
                int collectionId = collection.optInt("id");
                if (collectionId > 0) {
                    // [FIX VĐ 6] Thêm &include_adult=false
                    String collectionUrl = BASE_URL + "/collection/" + collectionId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
                    String collectionResp = restTemplate.getForObject(collectionUrl, String.class);
                    JSONObject collectionJson = new JSONObject(collectionResp);
                    JSONArray parts = collectionJson.optJSONArray("parts");
                    
                    if (parts != null && parts.length() > 0) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i); 
                            int partTmdbId = part.optInt("id");
                            if (addedMovieIds.contains(partTmdbId)) continue;
                            
                            Movie movie = movieService.syncMovieFromList(part); 
                            if (movie != null) {
                                finalRecommendations.add(movieService.convertToMap(movie));
                                addedMovieIds.add(partTmdbId); 
                            }
                        }
                        if (!finalRecommendations.isEmpty()) {
                            response.put("title", "🎬 Từ Bộ Sưu Tập: " + collectionJson.optString("name")); // Sửa Model
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi (load collection): " + e.getMessage());
        }
        
        // [FIX VĐ 6] Thêm &include_adult=false
        String recommendUrl = BASE_URL + "/movie/" + tmdbIdStr + "/recommendations?api_key=" + API_KEY + "&language=vi-VN&include_adult=false";
        Map<String, Object> fallbackData = movieService.loadAndSyncPaginatedMovies(recommendUrl, 10);
        List<Map<String, Object>> fallbackMovies = (List<Map<String, Object>>) fallbackData.get("movies");

        for (Map<String, Object> movieMap : fallbackMovies) {
            // [SỬA LỖI LOGIC] Phải lấy tmdbId từ map (vì nó đã được convertToMap)
            Integer movieTmdbId = (Integer) movieMap.get("tmdbId");

            if (movieTmdbId != null && !addedMovieIds.contains(movieTmdbId)) {
                finalRecommendations.add(movieMap);
                addedMovieIds.add(movieTmdbId);
            }
        }
        return finalRecommendations;
    }
}