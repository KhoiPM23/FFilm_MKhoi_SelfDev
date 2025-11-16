package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // <-- THÊM
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

    // Xóa hàm home() cũ
// Thay thế bằng hàm home() MỚI này:

    @GetMapping("/")
    public String home(Model model) {
        try {
            int dbFetchLimit = 40;
            int finalCarouselLimit = 20;

            // 1. [SSR] Tải Phim Hot (Cho Banner và Carousel 1)
            Page<Movie> dbHotMovies = movieService.getHotMoviesFromDB(dbFetchLimit);
            String hotApiUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            List<Map<String, Object>> hotMovies = movieService.getMergedCarouselMovies(
                hotApiUrl, dbHotMovies, finalCarouselLimit, MovieService.SortBy.HOT);
            model.addAttribute("hotMovies", hotMovies);

            // 2. [SSR] Set Banner (dựa trên Phim Hot)
            setBanner(model, hotMovies);
            
            // 3. [CSR] Trả về danh sách RỖNG cho 4 carousel còn lại
            // JavaScript sẽ tải dữ liệu cho các carousel này
            model.addAttribute("newMovies", new ArrayList<>());
            model.addAttribute("animeMovies", new ArrayList<>());
            model.addAttribute("kidsMovies", new ArrayList<>());
            model.addAttribute("actionMovies", new ArrayList<>());

            return "index";
            
        } catch (Exception e) {
            System.err.println("ERROR in home(): " + e.getMessage());
            e.printStackTrace();
            ensureDefaultAttributes(model); // Hàm fallback
            return "index";
        }
    }

    /**
     * [SỬA LỖI GIẢI PHÁP 3] Hàm setBanner mới
     * Lấy banner từ danh sách hotMovies đã gộp (đã ưu tiên DB)
     */
    private void setBanner(Model model, List<Map<String, Object>> hotMovies) {
        try {
            if (hotMovies != null && !hotMovies.isEmpty()) {
                // Lấy phim đầu tiên trong danh sách (đã ưu tiên DB)
                Map<String, Object> bannerMap = hotMovies.get(0);
                
                // Lấy movieID (PK)
                int movieID = (int) bannerMap.get("id");
                
                // [FIX] Gọi API (đã sửa) bằng movieID (PK)
                String trailerKey = movieService.findBestTrailerKey(movieID);
                String logoPath = movieService.findBestLogoPath(movieID);
                
                bannerMap.put("trailerKey", trailerKey); 
                bannerMap.put("logoPath", logoPath);     
                model.addAttribute("banner", bannerMap);
                return;
            }
        } catch (Exception e) { System.err.println("Error in setBanner (Mới): " + e.getMessage()); }
        
        // Fallback nếu có lỗi
        model.addAttribute("banner", createDefaultBanner());
    }
    
    // (Các hàm createDefaultBanner và ensureDefaultAttributes giữ nguyên)
    
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("id", 550); // Đây là movieID (PK), không phải tmdbId
        banner.put("title", "Welcome to FFilm");
        banner.put("overview", "Khám phá thế giới phim ảnh");
        banner.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        banner.put("rating", "8.5"); banner.put("year", "2025"); banner.put("runtime", 120);
        banner.put("country", "Việt Nam");
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