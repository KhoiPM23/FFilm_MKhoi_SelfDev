package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Controller
public class MovieDetailController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";
    
    @Autowired
    private MovieService movieService;
 
    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    // Fallback nếu không có bean RestTemplate
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }
        return restTemplate;
    }

    /**
     * Hiển thị trang chi tiết phim
     * Hỗ trợ 2 cách:
     * 1. /movie/detail/{id} - Path variable (ưu tiên)
     * 2. /movie/detail?id=123 - Query param (fallback)
     */
    @GetMapping({"/movie/detail/{id}", "/movie/detail"})
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId,
            Model model
    ) {
        RestTemplate rest = getRestTemplate();
        String finalId = null;
        
        try {
            // Ưu tiên path variable, fallback sang query param
            finalId = (id != null && !id.isEmpty()) ? id : movieId;
            
            if (finalId == null || finalId.isEmpty()) {
                model.addAttribute("error", "Movie ID không hợp lệ");
                model.addAttribute("errorDetails", "Vui lòng cung cấp ID phim hợp lệ");
                return "error";
            }
            
            System.out.println("🔍 Loading movie detail for ID: " + finalId);
            
            // Fetch movie details from TMDB với retry logic
            String detailUrl = BASE_URL + "/movie/" + finalId + 
                              "?api_key=" + API_KEY + "&language=vi-VN";
            
            String response = null;
            int retries = 3;
            int delay = 1000; // 1 second
            
            for (int i = 0; i < retries; i++) {
                try {
                    response = rest.getForObject(detailUrl, String.class);
                    if (response != null && !response.isEmpty()) {
                        System.out.println("✅ Successfully fetched movie data on attempt " + (i + 1));
                        break;
                    }
                } catch (Exception retryException) {
                    System.err.println("⚠️ Attempt " + (i + 1) + " failed: " + retryException.getMessage());
                    if (i < retries - 1) {
                        Thread.sleep(delay);
                        delay *= 2; // Exponential backoff
                    } else {
                        throw retryException; // Throw on last attempt
                    }
                }
            }
            
            if (response == null || response.isEmpty()) {
                System.err.println("❌ No response after " + retries + " attempts");
                // Fallback: create minimal movie data for client-side loading
                return createClientSideFallback(finalId, model);
            }
            
            JSONObject movieJson = new JSONObject(response);
            
            // Build movie data map
            Map<String, Object> movieData = new HashMap<>();
            movieData.put("id", finalId);
            movieData.put("title", movieJson.optString("title", "Unknown"));
            movieData.put("overview", movieJson.optString("overview", "Chưa có mô tả"));
            
            String backdropPath = movieJson.optString("backdrop_path", "");
            movieData.put("backdrop", !backdropPath.isEmpty() ? 
                IMAGE_BASE_URL + "/original" + backdropPath : 
                "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
            
            String posterPath = movieJson.optString("poster_path", "");
            movieData.put("poster", !posterPath.isEmpty() ? 
                IMAGE_BASE_URL + "/w500" + posterPath : "/images/placeholder.jpg");
            
            movieData.put("rating", String.format("%.1f", movieJson.optDouble("vote_average", 0.0)));
            movieData.put("releaseDate", movieJson.optString("release_date", ""));
            movieData.put("runtime", movieJson.optInt("runtime", 0));
            
            // Get genres
            JSONArray genresArray = movieJson.optJSONArray("genres");
            List<String> genreList = new ArrayList<>();
            if (genresArray != null) {
                for (int i = 0; i < genresArray.length(); i++) {
                    JSONObject genre = genresArray.getJSONObject(i);
                    genreList.add(genre.optString("name"));
                }
            }
            movieData.put("genres", genreList);
            
            // Get production countries
            JSONArray countriesArray = movieJson.optJSONArray("production_countries");
            if (countriesArray != null && countriesArray.length() > 0) {
                movieData.put("country", countriesArray.getJSONObject(0).optString("name"));
            } else {
                movieData.put("country", "—");
            }
            
            // Get spoken languages
            JSONArray languagesArray = movieJson.optJSONArray("spoken_languages");
            if (languagesArray != null && languagesArray.length() > 0) {
                movieData.put("language", languagesArray.getJSONObject(0).optString("name"));
            } else {
                movieData.put("language", "—");
            }
            
            // Get director from credits (with retry)
            try {
                String creditsUrl = BASE_URL + "/movie/" + finalId + 
                                  "/credits?api_key=" + API_KEY;
                String creditsResp = null;
                
                for (int i = 0; i < 2; i++) {
                    try {
                        creditsResp = restTemplate.getForObject(creditsUrl, String.class);
                        if (creditsResp != null) break;
                    } catch (Exception e) {
                        if (i == 0) Thread.sleep(500);
                    }
                }
                
                if (creditsResp != null) {
                    JSONObject creditsJson = new JSONObject(creditsResp);
                    JSONArray crew = creditsJson.optJSONArray("crew");
                    
                    if (crew != null) {
                        for (int i = 0; i < crew.length(); i++) {
                            JSONObject person = crew.getJSONObject(i);
                            if ("Director".equals(person.optString("job"))) {
                                movieData.put("director", person.optString("name"));
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Could not fetch director: " + e.getMessage());
            }
            
            if (!movieData.containsKey("director")) {
                movieData.put("director", "—");
            }
            
            // Add to model
            model.addAttribute("movie", movieData);
            model.addAttribute("movieId", finalId);
            
            System.out.println("✅ Movie detail loaded successfully: " + movieData.get("title"));
            
            return "movie/movie-detail";  // ← SỬA ĐÂY: thêm "movie/" prefix
            
        } catch (Exception e) {
            System.err.println("❌ Error loading movie detail: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback to client-side loading
            if (finalId != null && !finalId.isEmpty()) {
                return createClientSideFallback(finalId, model);
            }
            
            model.addAttribute("error", "Lỗi khi tải thông tin phim");
            model.addAttribute("errorDetails", e.getMessage());
            return "error";
        }
    }
    
    /**
     * Tạo fallback page cho trường hợp API fail
     * Client-side JS sẽ load data
     */
    private String createClientSideFallback(String movieId, Model model) {
        System.out.println("⚠️ Using client-side fallback for movie ID: " + movieId);
        
        Map<String, Object> movieData = new HashMap<>();
        movieData.put("id", movieId);
        movieData.put("title", "Đang tải...");
        movieData.put("overview", "Đang tải thông tin phim...");
        movieData.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        movieData.put("poster", "/images/placeholder.jpg");
        movieData.put("rating", "0.0");
        movieData.put("releaseDate", "");
        movieData.put("runtime", 0);
        movieData.put("genres", new ArrayList<String>());
        movieData.put("country", "—");
        movieData.put("language", "—");
        movieData.put("director", "—");
        
        model.addAttribute("movie", movieData);
        model.addAttribute("movieId", movieId);
        model.addAttribute("clientSideLoad", true);
        
        return "movie/movie-detail";  // ← SỬA ĐÂY: thêm "movie/" prefix
    }
    
    // lấy dữ liệu cho lớp player of Nguyên
    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id, Model model) {
        try{
            Movie movie = movieService.getMovieById(id); 
            model.addAttribute("movie", movie);

            List<Movie> recommendedMovies = movieService.getRecommendedMovies();
            recommendedMovies.removeIf(m -> m.getMovieID() == id);
            model.addAttribute("recommendedMovies", recommendedMovies);
            return "movie/player";
        } catch (RuntimeException e){
            System.err.println("Lỗi: " +e.getMessage());
            return "redirect:/";
        }
    }
    
}