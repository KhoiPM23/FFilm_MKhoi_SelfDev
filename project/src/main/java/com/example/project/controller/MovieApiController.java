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
// Thêm CrossOrigin
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate; 

import java.util.ArrayList; 
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet; 
import java.util.List; 
import java.util.Map;
import java.util.Set; 
import java.util.stream.Collectors;
import java.util.Optional; 
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


@RestController
@RequestMapping("/api/movie")
public class MovieApiController {

    //---- 1. CẤU HÌNH & REPOSITORY ----

    @Autowired
    private MovieService movieService;

    @Autowired
    private RestTemplate restTemplate;

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    //---- 2. API CORE SYNC (SEARCH & SYNC) ----

    // API tìm phim theo TÊN trong DB (Dùng cho Live Suggestion)
    @GetMapping("/search-db")
    public ResponseEntity<List<Map<String, Object>>> liveSearchDb(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        
        //----- Tìm kiếm và convert dữ liệu đã có trong DB
        List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
        List<Map<String, Object>> mappedResults = dbResults.stream()
            .map(movie -> movieService.convertToMap(movie)) 
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(mappedResults);
    }
    
    // API kiểm tra phim theo TMDB ID (Sync Lazy nếu thiếu, trả về Map)
    @PostMapping("/check-db")
    public ResponseEntity<Map<Integer, Map<String, Object>>> checkDbForMovies(@RequestBody List<Integer> tmdbIds) {
        try {
            Map<Integer, Map<String, Object>> dbMoviesMap = movieService.getMoviesByTmdbIds(tmdbIds);
            
            //----- Lọc ID bị thiếu
            List<Integer> missingIds = new ArrayList<>();
            for (Integer tmdbId : tmdbIds) {
                if (!dbMoviesMap.containsKey(tmdbId)) {
                    missingIds.add(tmdbId);
                }
            }

            //----- Tạo bản ghi Lazy (bản "cụt") cho ID bị thiếu
            for (Integer tmdbId : missingIds) {
                try {
                    String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN";
                    String resp = restTemplate.getForObject(url, String.class);
                    
                    if (resp != null) {
                        Movie movie = movieService.syncMovieFromList(new JSONObject(resp));
                        if (movie != null) {
                            dbMoviesMap.put(tmdbId, movieService.convertToMap(movie));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo lazy-load cho ID (check-db): " + tmdbId + " - " + e.getMessage());
                }
            }
            
            return ResponseEntity.ok(dbMoviesMap);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyMap()); 
        }
    }

    // API đồng bộ nhanh 1 ID (Tạo hoặc nâng cấp bản EAGER, trả về Map)
    @GetMapping("/sync-by-tmdbid/{tmdbId}")
    public ResponseEntity<?> syncByTmdbId(@PathVariable int tmdbId) {
        try {
            // Kiểm tra DB trước
            Optional<Movie> existing = movieService.getMovieRepository().findByTmdbId(tmdbId);
            Movie movie;
            
            if (existing.isPresent()) {
                // Nâng cấp "vừa" (để lấy poster/rating nếu thiếu)
                movie = movieService.getMoviePartial(tmdbId);
            } else {
                // Tạo mới bản Lazy (nếu chưa có)
                String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&language=vi-VN&include_adult=false"; 
                String resp = restTemplate.getForObject(url, String.class);
                
                // ✅ FIX: Kiểm tra response
                if (resp == null || resp.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "success", false,
                            "message", "Không tìm thấy phim trên TMDB (ID: " + tmdbId + ")"
                        ));
                }
                
                movie = movieService.syncMovieFromList(new JSONObject(resp));
                
                // ✅ FIX: Kiểm tra kết quả sync
                if (movie == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                            "success", false,
                            "message", "Phim bị filter (spam/adult) hoặc dữ liệu không hợp lệ"
                        ));
                }
            }

            // ✅ Double-check movie không null
            if (movie == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "Lỗi xử lý dữ liệu phim"
                    ));
            }
            
            // Trả về Map (đã có movieID PK)
            return ResponseEntity.ok(movieService.convertToMap(movie));
            
        } catch (Exception e) {
            System.err.println("❌ Lỗi sync-by-tmdbid: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false, 
                    "message", "Lỗi server: " + e.getMessage()
                ));
        }
    }

    //---- 3. API DETAIL / UTILITY ----
    
    // Lấy chi tiết Hover Card (Gọi bằng movieID PK)
    @GetMapping("/hover-detail/{id}")
    public ResponseEntity<?> getHoverDetail(@PathVariable("id") int movieID) {
        try {
            //----- Lấy movie và nâng cấp (EAGER) nếu cần
            Movie movie = movieService.getMovieByIdOrSync(movieID); 
            if (movie == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> movieData = movieService.convertToMap(movie);
            
            //----- Lấy trailer key (nếu có)
            String trailerKey = null;
            if (movie.getTmdbId() != null) {
                trailerKey = movieService.findBestTrailerKey(movieID);
            }

            //----- Trả về data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("movie", movieData);
            responseData.put("trailerKey", trailerKey);
            return ResponseEntity.ok(responseData);
            
        } catch (Exception e) {
            System.err.println("Lỗi API getHoverDetail cho movieID " + movieID + ": " + e.getMessage());
            return ResponseEntity.status(500).body("Lỗi server");
        }
    }

    // Lấy chi tiết Banner (Logo Path, Trailer Key) (Gọi bằng movieID PK)
    @GetMapping("/banner-detail/{id}")
    public ResponseEntity<?> getBannerDetail(@PathVariable("id") int movieID) {
        try {
            //----- Gọi Service bằng PK
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

    //---- 4. API HOME CAROUSELS (ASYNC LOAD) ----

    // API tải Phim Mới (Sort NEW)
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

    // API tải Anime (Sort HOT)
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

    // API tải Phim Trẻ Em (Sort HOT)
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

    // API tải Phim Hành Động (Sort HOT)
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

    //---- 5. API DETAIL CAROUSELS (ASYNC LOAD) ----

    // API tải Trending (dùng cho sidebar)
    @GetMapping("/{id}/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrendingSidebar(@PathVariable("id") int movieID) {
        // movieID ở đây không thực sự cần, nhưng để API nhất quán
        return ResponseEntity.ok(loadTrendingSidebar());
    }

    // API tải Similar (Phim tương tự)
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<Map<String, Object>>> getSimilarMovies(@PathVariable("id") int movieID) {
        Movie movie = movieService.getMovieById(movieID); 
        if (movie == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        return ResponseEntity.ok(loadSimilarMovies(movie));
    }

    // API tải Recommended (Phim đề xuất / Collection)
    @GetMapping("/{id}/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedMovies(@PathVariable("id") int movieID) {
        Movie movie = movieService.getMovieById(movieID); 
        if (movie == null) {
            return ResponseEntity.ok(Map.of("title", "Phim Khác", "movies", new ArrayList<>()));
        }
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> movies = movieService.getRecommendedMoviesWaterfall(movie, response);
        
        response.put("movies", movies);
        if (!response.containsKey("title")) {
            response.put("title", "✨ Có Thể Bạn Thích"); 
        }
        return ResponseEntity.ok(response);
    }

    //---- 6. PRIVATE HELPERS (DETAIL CAROUSELS) ----

    // Helper: Tải Trending (dùng chung cho sidebar)
    private List<Map<String, Object>> loadTrendingSidebar() {
        String url = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN";
        Page<Movie> dbHotMovies = movieService.getHotMoviesFromDB(40);
        return movieService.getMergedCarouselMovies(url, dbHotMovies, 10, MovieService.SortBy.HOT);
    }

    // Helper: Tải Similar Movies
    private List<Map<String, Object>> loadSimilarMovies(Movie movie) {
        String apiUrl;
        Page<Movie> dbMovies;
        int dbFetchLimit = 40;
        int limit = 10;
        Integer tmdbId = movie.getTmdbId();

        //----- 1. Xác định Nguồn API
        if (tmdbId != null) {
            apiUrl = BASE_URL + "/movie/" + tmdbId + "/similar?api_key=" + API_KEY + "&language=vi-VN";
        } else {
            apiUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
        }

        //----- 2. Xác định Nguồn DB (Dùng Genre đầu tiên)
        List<Genre> genres = movie.getGenres(); 
        if (genres != null && !genres.isEmpty()) {
            Integer firstGenreId = genres.get(0).getTmdbGenreId();
            dbMovies = movieService.getMoviesByGenreFromDB(firstGenreId, dbFetchLimit, 0); 
        } else {
            dbMovies = movieService.getHotMoviesFromDB(dbFetchLimit);
        }

        //----- 3. Gộp và Sort (HOT)
        List<Map<String, Object>> merged = movieService.getMergedCarouselMovies(
            apiUrl, 
            dbMovies, 
            limit, 
            MovieService.SortBy.HOT
        );

        //----- 4. Lọc bỏ chính phim đang xem
        return merged.stream()
            .filter(m -> (Integer)m.get("id") != movie.getMovieID()) 
            .collect(Collectors.toList());
    }
}