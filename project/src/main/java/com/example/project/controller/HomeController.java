package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

    //---- 1. CẤU HÌNH & REPOSITORY ----

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MovieService movieService; 

    //---- 2. MAIN HOME LOGIC ----

    // Hiển thị trang chủ
    @GetMapping("/")
    public String home(Model model) {
        try {
            int dbFetchLimit = 40;
            int finalCarouselLimit = 20;

            //----- 1. Tải Phim Hot (SSR - cho Banner và Carousel 1)
            Page<Movie> dbHotMovies = movieService.getHotMoviesFromDB(dbFetchLimit);
            String hotApiUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            
            List<Map<String, Object>> hotMovies = movieService.getMergedCarouselMovies(
                hotApiUrl, dbHotMovies, finalCarouselLimit, MovieService.SortBy.HOT);
            model.addAttribute("hotMovies", hotMovies);

            //----- 2. Set Banner (dựa trên Phim Hot đã gộp)
            setBanner(model, hotMovies);
            
            //----- 3. Trả về danh sách RỖNG cho 4 carousel còn lại (tải bất đồng bộ bằng JS)
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

    //---- 3. HELPER FUNCTIONS ----

    // Set Banner từ danh sách phim đã gộp
    private void setBanner(Model model, List<Map<String, Object>> hotMovies) {
        try {
            if (hotMovies != null && !hotMovies.isEmpty()) {
                Map<String, Object> bannerMap = hotMovies.get(0);
                int movieID = (int) bannerMap.get("id");
                
                //----- Gọi API Service bằng movieID (PK)
                String trailerKey = movieService.findBestTrailerKey(movieID);
                String logoPath = movieService.findBestLogoPath(movieID);
                
                bannerMap.put("trailerKey", trailerKey); 
                bannerMap.put("logoPath", logoPath);     
                model.addAttribute("banner", bannerMap);
                return;
            }
        } catch (Exception e) { System.err.println("Error in setBanner: " + e.getMessage()); }
        
        // Fallback nếu có lỗi
        model.addAttribute("banner", createDefaultBanner());
    }
    
    // Tạo Banner mặc định (khi không có dữ liệu)
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("id", 550); // Movie ID (PK) mặc định
        banner.put("title", "Welcome to FFilm");
        banner.put("overview", "Khám phá thế giới phim ảnh");
        banner.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        banner.put("rating", "8.5"); banner.put("year", "2025"); banner.put("runtime", 120);
        banner.put("country", "Việt Nam");
        return banner;
    }
    
    // Đảm bảo các thuộc tính có tồn tại khi có lỗi (tránh lỗi Thymeleaf)
    private void ensureDefaultAttributes(Model model) {
        if (!model.containsAttribute("banner")) model.addAttribute("banner", createDefaultBanner());
        model.addAttribute("hotMovies", new ArrayList<>());
        model.addAttribute("newMovies", new ArrayList<>());
        model.addAttribute("animeMovies", new ArrayList<>());
        model.addAttribute("kidsMovies", new ArrayList<>());
        model.addAttribute("actionMovies", new ArrayList<>());
    }
}