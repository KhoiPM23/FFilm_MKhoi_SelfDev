package com.example.project.controller;

import com.example.project.model.Genre;
import com.example.project.model.Movie;
import com.example.project.model.Person; 
import com.example.project.service.MovieService;
import org.json.JSONArray; 
import org.json.JSONObject; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import java.util.Optional; // <-- THÊM
import org.json.JSONObject; // <-- THÊM
import org.springframework.data.domain.Page;
import java.util.Comparator;



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

    /**
     * [MỚI - FIX VĐ 4 & 6]
     * API này nhận tmdbId, đồng bộ (sync) nó vào DB nếu chưa có,
     * và LUÔN LUÔN trả về Map của phim (đã có movieID PK và POSTER/RATING)
     * Endpoint: GET /api/movie/sync-by-tmdbid/{tmdbId}
     */
    @GetMapping("/sync-by-tmdbid/{tmdbId}")
    public ResponseEntity<?> syncByTmdbId(@PathVariable int tmdbId) {
        try {
            // 1. Tìm trong DB trước
            Optional<Movie> existing = movieService.getMovieRepository().findByTmdbId(tmdbId);
            
            Movie movie;
            if (existing.isPresent()) {
                // 2a. Nếu có, nâng cấp "vừa" (để lấy poster/rating nếu thiếu)
                movie = movieService.getMoviePartial(tmdbId);
            } else {
                // 2b. Nếu chưa có, gọi API chi tiết 1 LẦN
                String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false"; 
                String resp = restTemplate.getForObject(url, String.class);
                if (resp == null) throw new RuntimeException("Không tìm thấy phim trên TMDB");
                
                // Dùng hàm Lazy (an toàn) để TẠO MỚI
                movie = movieService.syncMovieFromList(new JSONObject(resp));
            }

            if (movie == null) {
                 return ResponseEntity.notFound().build();
            }
            
            // 3. Trả về Map (đã có movieID PK)
            return ResponseEntity.ok(movieService.convertToMap(movie));
            
        } catch (Exception e) {
            System.err.println("Lỗi sync-by-tmdbid: " + e.getMessage());
            // [FIX LỖI NON-JSON] Luôn trả về ResponseEntity với JSON error body
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("success", false, 
                                             "message", e.getMessage()));
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

    // Dán 4 hàm MỚI này vào bên trong class MovieApiController (ví dụ: ngay trên hàm helper `loadTrendingSidebar`)

    // ===============================================
    // [MỚI] API TẢI BẤT ĐỒNG BỘ CHO TRANG CHỦ (INDEX)
    // ===============================================

    /**
     * API tải Phim Mới (Sort NEW)
     */
    @GetMapping("/home/new")
    public ResponseEntity<List<Map<String, Object>>> getHomeNewMovies() {
        int dbFetchLimit = 40;
        int finalCarouselLimit = 20;
        
        Page<Movie> dbNewMovies = movieService.getNewMoviesFromDB(dbFetchLimit);
        String newApiUrl = BASE_URL + "/movie/now_playing?api_key=" + API_KEY + "&language=vi-VN&page=1";
        
        List<Map<String, Object>> movies = movieService.getMergedCarouselMovies(
            newApiUrl, dbNewMovies, finalCarouselLimit, MovieService.SortBy.NEW);
            
        return ResponseEntity.ok(movies);
    }

    /**
     * API tải Anime (Sort HOT)
     */
    @GetMapping("/home/anime")
    public ResponseEntity<List<Map<String, Object>>> getHomeAnimeMovies() {
        int dbFetchLimit = 40;
        int finalCarouselLimit = 20;
        
        Page<Movie> dbAnime = movieService.getMoviesByGenreFromDB(16, dbFetchLimit, 0); // 16 = Hoạt hình
        String animeApiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=16&sort_by=popularity.desc&page=1";
        
        List<Map<String, Object>> movies = movieService.getMergedCarouselMovies(
            animeApiUrl, dbAnime, finalCarouselLimit, MovieService.SortBy.HOT);
            
        return ResponseEntity.ok(movies);
    }

    /**
     * API tải Phim Trẻ Em (Sort HOT)
     */
    @GetMapping("/home/kids")
    public ResponseEntity<List<Map<String, Object>>> getHomeKidsMovies() {
        int dbFetchLimit = 40;
        int finalCarouselLimit = 20;
        
        Page<Movie> dbKids = movieService.getMoviesByGenreFromDB(10751, dbFetchLimit, 0); // 10751 = Gia đình
        String kidsApiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=10751&sort_by=popularity.desc&page=1";
        
        List<Map<String, Object>> movies = movieService.getMergedCarouselMovies(
            kidsApiUrl, dbKids, finalCarouselLimit, MovieService.SortBy.HOT);
            
        return ResponseEntity.ok(movies);
    }

    /**
     * API tải Phim Hành Động (Sort HOT)
     */
    @GetMapping("/home/action")
    public ResponseEntity<List<Map<String, Object>>> getHomeActionMovies() {
        int dbFetchLimit = 40;
        int finalCarouselLimit = 20;
        
        Page<Movie> dbAction = movieService.getMoviesByGenreFromDB(28, dbFetchLimit, 0); // 28 = Hành động
        String actionApiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=28&sort_by=popularity.desc&page=1";
        
        List<Map<String, Object>> movies = movieService.getMergedCarouselMovies(
            actionApiUrl, dbAction, finalCarouselLimit, MovieService.SortBy.HOT);
            
        return ResponseEntity.ok(movies);
    }

    // --- CÁC HÀM HELPER (loadTrendingSidebar, v.v.) BẮT ĐẦU TỪ ĐÂY ---

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
        Movie movie = movieService.getMovieById(movieID); // [CITE: MovieService.java]
        if (movie == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        // [SỬA] Truyền toàn bộ object 'movie' vào helper
        return ResponseEntity.ok(loadSimilarMovies(movie));
    }

    /**
     * API tải Recommended (Phim đề xuất / Collection)
     * Endpoint: GET /api/movie/{id}/recommended
     */
    @GetMapping("/{id}/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedMovies(@PathVariable("id") int movieID) {
        Movie movie = movieService.getMovieById(movieID); // Lấy movie bằng PK
        if (movie == null) {
            // Phim tự tạo (hoặc lỗi)
            return ResponseEntity.ok(Map.of("title", "Phim Khác", "movies", new ArrayList<>()));
        }
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> movies = loadRecommendedMovies(movie, response);
        
        response.put("movies", movies);
        if (!response.containsKey("title")) {
            response.put("title", "✨ Có Thể Bạn Thích"); // Tiêu đề fallback cuối cùng
        }
        return ResponseEntity.ok(response);
    }

    // (Helper 1)
    private List<Map<String, Object>> loadTrendingSidebar() {
        // [SỬA VĐ 6] Dùng logic Gộp (Sort HOT)
        String url = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN";
        Page<Movie> dbHotMovies = movieService.getHotMoviesFromDB(40);
        return movieService.getMergedCarouselMovies(url, dbHotMovies, 10, MovieService.SortBy.HOT);
    }

    /**
     * [SỬA VĐ 6] Helper cho "Phim tương tự" - Dùng Genre làm nguồn DB
     */
    private List<Map<String, Object>> loadSimilarMovies(Movie movie) { // [SỬA] Nhận Movie object
        String apiUrl;
        Page<Movie> dbMovies;
        int dbFetchLimit = 40;
        int limit = 10;
        Integer tmdbId = movie.getTmdbId(); // [CITE: Movie.java]

        // 1. Xác định Nguồn API (TMDB)
        if (tmdbId != null) {
            // Nếu có tmdbId, dùng API Similar
            apiUrl = BASE_URL + "/movie/" + tmdbId + "/similar?api_key=" + API_KEY + "&language=vi-VN";
        } else {
            // Phim custom không có tmdbId, dùng tạm API Popular.
            apiUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
        }

        // 2. [SỬA VĐ 6] Xác định Nguồn DB (Dùng Genre)
        List<Genre> genres = movie.getGenres(); // [CITE: Movie.java]
        if (genres != null && !genres.isEmpty()) {
            // Lấy ID thể loại đầu tiên của phim đang xem
            Integer firstGenreId = genres.get(0).getTmdbGenreId(); // [CITE: Genre.java]
            
            // Lấy 40 phim DB CÙNG THỂ LOẠI
            dbMovies = movieService.getMoviesByGenreFromDB(firstGenreId, dbFetchLimit, 0); // [CITE: MovieService.java]
        } else {
            // Fallback: Nếu phim (custom) không có thể loại, dùng HOT DB
            dbMovies = movieService.getHotMoviesFromDB(dbFetchLimit); // [CITE: MovieService.java]
        }

        // 3. Gộp và Sort (HOT)
        List<Map<String, Object>> merged = movieService.getMergedCarouselMovies(
            apiUrl, 
            dbMovies, 
            limit, 
            MovieService.SortBy.HOT // "Phim tương tự" ưu tiên HOT
        );

        // 4. Lọc bỏ chính nó (phim đang xem) ra khỏi danh sách
        return merged.stream()
            .filter(m -> (Integer)m.get("id") != movie.getMovieID()) // Lọc bỏ phim đang xem (dựa trên PK)
            .collect(Collectors.toList());
    }

    /**
     * [SỬA LỖI] Helper cho "Có Thể Bạn Thích" - Đã chuyển logic về Service
     * Hàm này giờ chỉ gọi MovieService
     */
    private List<Map<String, Object>> loadRecommendedMovies(Movie movie, Map<String, Object> response) {
        // [FIX] Gọi hàm Waterfall mới (đã nằm trong MovieService)
        return movieService.getRecommendedMoviesWaterfall(movie, response);
    }
}