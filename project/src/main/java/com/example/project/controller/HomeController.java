package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import com.example.project.model.Movie; // BỔ SUNG
import com.example.project.service.MovieService; // BỔ SUNG

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired; // BỔ SUNG

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat; // BỔ SUNG

@Controller
public class HomeController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

    // === BỔ SUNG DEPENDENCIES ===
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MovieService movieService;
    // ============================

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Banner - Phim phổ biến nhất (SỬ DỤNG LOGIC SYNC MỚI)
            setBanner(model, restTemplate);
            
            // Các danh mục phim (SỬ DỤNG LOGIC SYNC MỚI)
            setHotMovies(model, restTemplate);
            setNewReleases(model, restTemplate);
            setAnimeHot(model, restTemplate);
            setKidsMovies(model, restTemplate);
            setActionMovies(model, restTemplate);

            return "index";
        } catch (Exception e) {
            System.err.println("ERROR in home(): " + e.getMessage());
            e.printStackTrace();
            
            ensureDefaultAttributes(model);
            return "index";
        }
    }

    // === SỬA LẠI HOÀN TOÀN TẤT CẢ CÁC HÀM BÊN DƯỚI ===

    private void setBanner(Model model, RestTemplate restTemplate) {
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
                        // Dùng findOrImport để lấy đủ chi tiết (runtime, genres...)
                        // Hàm này sẽ tự động lưu phim vào DB nếu chưa có
                        Movie bannerMovie = movieService.findOrImportMovieByTmdbId(tmdbId);
                        
                        // Xử lý genres (vì findOrImport không tự map categories)
                        // (Bỏ qua bước này để đơn giản hóa, banner chỉ cần runtime)

                        // Tạo Map cho banner từ Movie entity
                        Map<String, Object> bannerMap = new HashMap<>();
                        bannerMap.put("id", bannerMovie.getTmdbId()); // Dùng TMDB ID
                        bannerMap.put("title", bannerMovie.getTitle());
                        bannerMap.put("overview", bannerMovie.getDescription());
                        bannerMap.put("backdrop", IMAGE_BASE_URL + "/original" + bannerMovie.getBackdropPath());
                        bannerMap.put("rating", String.format("%.1f", bannerMovie.getRating()));
                        bannerMap.put("year", bannerMovie.getReleaseDate() != null ? new SimpleDateFormat("yyyy").format(bannerMovie.getReleaseDate()) : "N/A");
                        bannerMap.put("runtime", bannerMovie.getDuration()); // Đã có runtime

                        model.addAttribute("banner", bannerMap);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in setBanner: " + e.getMessage());
        }
        model.addAttribute("banner", createDefaultBanner());
    }

    // Hàm helper mới, thay thế `getMoviesFromUrl`
    private List<Movie> syncMoviesFromUrl(RestTemplate restTemplate, String url, int maxCount) {
        List<Movie> syncedMovies = new ArrayList<>();
        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isEmpty()) {
                return syncedMovies;
            }
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");
            
            if (results == null) {
                return syncedMovies;
            }

            for (int i = 0; i < Math.min(maxCount, results.length()); i++) {
                try {
                    JSONObject item = results.getJSONObject(i);
                    // Bỏ qua phim không có poster
                    if (item.optString("poster_path", "").isEmpty()) {
                        continue;
                    }
                    
                    // Dùng hàm sync đã sửa lỗi (không ném lỗi @NotBlank)
                    Movie movie = movieService.syncMovieFromTmdbData(item);
                    if (movie != null) {
                        syncedMovies.add(movie);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing movie at index " + i + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching from: " + url + " - " + e.getMessage());
        }
        return syncedMovies;
    }


    private void setHotMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            model.addAttribute("hotMovies", syncMoviesFromUrl(restTemplate, url, 20));
        } catch (Exception e) {
            System.err.println("Error in setHotMovies: " + e.getMessage());
            model.addAttribute("hotMovies", new ArrayList<>());
        }
    }

    private void setNewReleases(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/movie/now_playing?api_key=" + API_KEY + "&language=vi-VN&page=1";
            model.addAttribute("newMovies", syncMoviesFromUrl(restTemplate, url, 20));
        } catch (Exception e) {
            System.err.println("Error in setNewReleases: " + e.getMessage());
            model.addAttribute("newMovies", new ArrayList<>());
        }
    }

    private void setAnimeHot(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=16&sort_by=popularity.desc&page=1";
            model.addAttribute("animeMovies", syncMoviesFromUrl(restTemplate, url, 20));
        } catch (Exception e) {
            System.err.println("Error in setAnimeHot: " + e.getMessage());
            model.addAttribute("animeMovies", new ArrayList<>());
        }
    }

    private void setKidsMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=10751&sort_by=popularity.desc&page=1";
            model.addAttribute("kidsMovies", syncMoviesFromUrl(restTemplate, url, 20));
        } catch (Exception e) {
            System.err.println("Error in setKidsMovies: " + e.getMessage());
            model.addAttribute("kidsMovies", new ArrayList<>());
        }
    }

    private void setActionMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=28&sort_by=popularity.desc&page=1";
            model.addAttribute("actionMovies", syncMoviesFromUrl(restTemplate, url, 20));
        } catch (Exception e) {
            System.err.println("Error in setActionMovies: " + e.getMessage());
            model.addAttribute("actionMovies", new ArrayList<>());
        }
    }
    
    // (Hàm createDefaultBanner và ensureDefaultAttributes giữ nguyên)
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("title", "Welcome to FFilm");
        banner.put("overview", "Discover amazing movies and TV shows");
        banner.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        banner.put("rating", "8.5");
        banner.put("year", "2024");
        banner.put("runtime", 120);
        return banner;
    }
    
    private void ensureDefaultAttributes(Model model) {
        if (!model.containsAttribute("banner")) {
            model.addAttribute("banner", createDefaultBanner());
        }
        model.addAttribute("hotMovies", new ArrayList<>());
        model.addAttribute("newMovies", new ArrayList<>());
        model.addAttribute("animeMovies", new ArrayList<>());
        model.addAttribute("kidsMovies", new ArrayList<>());
        model.addAttribute("actionMovies", new ArrayList<>());
    }
}