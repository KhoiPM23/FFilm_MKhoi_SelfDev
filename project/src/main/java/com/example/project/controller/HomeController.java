package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate; 

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MovieService movieService; // Cổng logic chính

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Tải Banner (Giữ nguyên logic G5)
            setBanner(model);
            
            // [G29] TỐI ƯU: Gọi hàm service chung
            int carouselLimit = 20; // Giới hạn 20 phim mỗi carousel

            // Gọi hàm mới và chỉ lấy danh sách "movies"
            Map<String, Object> hotData = movieService.loadAndSyncPaginatedMovies(
                BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1", carouselLimit
            );
            Map<String, Object> newData = movieService.loadAndSyncPaginatedMovies(
                BASE_URL + "/movie/now_playing?api_key=" + API_KEY + "&language=vi-VN&page=1", carouselLimit
            );
            Map<String, Object> animeData = movieService.loadAndSyncPaginatedMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=16&sort_by=popularity.desc&page=1", carouselLimit
            );
            Map<String, Object> kidsData = movieService.loadAndSyncPaginatedMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=10751&sort_by=popularity.desc&page=1", carouselLimit
            );
            Map<String, Object> actionData = movieService.loadAndSyncPaginatedMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=28&sort_by=popularity.desc&page=1", carouselLimit
            );

            // Gán danh sách phim (List<Map>) vào model
            model.addAttribute("hotMovies", (List<Map<String, Object>>) hotData.get("movies"));
            model.addAttribute("newMovies", (List<Map<String, Object>>) newData.get("movies"));
            model.addAttribute("animeMovies", (List<Map<String, Object>>) animeData.get("movies"));
            model.addAttribute("kidsMovies", (List<Map<String, Object>>) kidsData.get("movies"));
            model.addAttribute("actionMovies", (List<Map<String, Object>>) actionData.get("movies"));

            return "index";
        } catch (Exception e) {
            System.err.println("ERROR in home(): " + e.getMessage());
            e.printStackTrace();
            ensureDefaultAttributes(model); // Đảm bảo trang không bị crash
            return "index";
        }
    }

    /**
     * [G43] Sửa lỗi: Banner phải gọi hàm LAZY
     */
    private void setBanner(Model model) {
        try {
            String listUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            String listResp = restTemplate.getForObject(listUrl, String.class);

            if (listResp != null && !listResp.isEmpty()) {
                JSONObject listJson = new JSONObject(listResp);
                JSONArray results = listJson.optJSONArray("results");

                if (results != null && results.length() > 0) {
                    JSONObject firstMovieJson = results.getJSONObject(0);
                    int tmdbId = firstMovieJson.optInt("id", -1);

                    if (tmdbId > 0) {
                        // [G43] SỬA LỖI:
                        // Movie bannerMovie = movieService.getMovieOrSync(tmdbId); // LỖI (Eager)
                        Movie bannerMovie = movieService.getMoviePartial(tmdbId); // ĐÚNG (Lazy)
                        
                        Map<String, Object> bannerMap = movieService.convertToMap(bannerMovie);
                        String trailerKey = movieService.findBestTrailerKey(tmdbId);
                        String logoPath = movieService.findBestLogoPath(tmdbId);
                        bannerMap.put("trailerKey", trailerKey); 
                        bannerMap.put("logoPath", logoPath);     
                        model.addAttribute("banner", bannerMap);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in setBanner (G43): " + e.getMessage());
        }
        model.addAttribute("banner", createDefaultBanner());
    }
    
    /**
     * [G29] XÓA BỎ:
     * Hàm private loadMoviesFromApi(String endpoint) đã bị xóa
     * vì logic của nó đã được chuyển vào MovieService.loadAndSyncPaginatedMovies()
     */
    // private List<Map<String, Object>> loadMoviesFromApi(String endpoint) { ... }


    // (Các hàm createDefaultBanner và ensureDefaultAttributes giữ nguyên)
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("id", 550); banner.put("title", "Welcome to FFilm");
        banner.put("overview", "Khám phá thế giới phim ảnh");
        banner.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        banner.put("rating", "8.5"); banner.put("year", "2025"); banner.put("runtime", 120);
        return banner;
    }
    
    private void ensureDefaultAttributes(Model model) {
        if (!model.containsAttribute("banner")) model.addAttribute("banner", createDefaultBanner());
        model.addAttribute("hotMovies", new ArrayList<>());
        model.addAttribute("newMovies", new ArrayList<>());
        model.addAttribute("animeMovies", new ArrayList<>());
        model.addAttribute("kidsMovies", new ArrayList<>());
        model.addAttribute("actionMovies", new ArrayList<>());
    }
}