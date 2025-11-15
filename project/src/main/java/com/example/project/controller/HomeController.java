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
    private MovieService movieService; 

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Tải Banner (Giữ nguyên logic G43 - dùng getMoviePartial)
            setBanner(model);
            
            // [SỬA] Tối ưu: Gọi hàm helper mới (loadCarouselMovies)
            int carouselLimit = 10;

            model.addAttribute("hotMovies", loadCarouselMovies(
                BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1", 20
            ));
            model.addAttribute("newMovies", loadCarouselMovies(
                BASE_URL + "/movie/now_playing?api_key=" + API_KEY + "&language=vi-VN&page=1", carouselLimit
            ));
            model.addAttribute("animeMovies", loadCarouselMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=16&sort_by=popularity.desc&page=1", carouselLimit
            ));
            model.addAttribute("kidsMovies", loadCarouselMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=10751&sort_by=popularity.desc&page=1", carouselLimit
            ));
            model.addAttribute("actionMovies", loadCarouselMovies(
                BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=28&sort_by=popularity.desc&page=1", carouselLimit
            ));

            return "index";
        } catch (Exception e) {
            System.err.println("ERROR in home(): " + e.getMessage());
            e.printStackTrace();
            ensureDefaultAttributes(model);
            return "index";
        }
    }

    /**
     * [THÊM MỚI] Hàm helper (của Claude) để load carousel nhanh
     * (Chỉ dùng syncMovieFromList - LAZY)
     */
    private List<Map<String, Object>> loadCarouselMovies(String apiUrl, int limit) {
        List<Map<String, Object>> movies = new ArrayList<>();
        try {
            String response = restTemplate.getForObject(apiUrl, String.class);
            if (response == null) return movies;
            
            JSONArray results = new JSONObject(response).optJSONArray("results");
            if (results == null) return movies;
            
            for (int i = 0; i < Math.min(results.length(), limit); i++) {
                JSONObject item = results.getJSONObject(i);
                // Chỉ gọi hàm LAZY
                Movie movie = movieService.syncMovieFromList(item); 
                if (movie != null) {
                    movies.add(movieService.convertToMap(movie));
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi loadCarouselMovies: " + e.getMessage());
        }
        return movies;
    }

    // (Hàm setBanner, createDefaultBanner, ensureDefaultAttributes giữ nguyên y như cũ)
    
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
                        // Banner PHẢI DÙNG getMoviePartial để lấy duration/country
                        Movie bannerMovie = movieService.getMoviePartial(tmdbId); 
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
        } catch (Exception e) { System.err.println("Error in setBanner (G43): " + e.getMessage()); }
        model.addAttribute("banner", createDefaultBanner());
    }
    
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