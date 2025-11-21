package com.example.project.controller;

import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MovieDetailController {

    @Autowired private MovieService movieService;

    @GetMapping({"/movie/detail/{id}", "/movie/detail"})
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId,
            Model model
    ) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : movieId;
        if (finalIdStr == null || finalIdStr.isEmpty()) return "redirect:/";

        try {
            int movieID = Integer.parseInt(finalIdStr);
            
            // [FIX] Gọi hàm Service an toàn (đã bao gồm @Transactional và convert)
            // Map này đã chứa đầy đủ: info, trailer, logo, castList
            Map<String, Object> movieMap = movieService.getMovieDetailMap(movieID);

            // 1. Xử lý Cast List (đã được Service nhét vào key 'castList')
            List<?> castList = (List<?>) movieMap.get("castList");
            model.addAttribute("castList", castList);

            // 2. Xử lý Trailer (đã được Service nhét vào key 'trailerKey')
            List<Map<String, Object>> trailers = new ArrayList<>();
            String tKey = (String) movieMap.get("trailerKey");
            if (tKey != null && !tKey.isEmpty()) {
                Map<String, Object> t = new HashMap<>();
                t.put("key", tKey);
                t.put("name", "Trailer Chính Thức");
                trailers.add(t);
            }
            model.addAttribute("trailers", trailers);
            
            // 3. Các Attributes khác
            model.addAttribute("movie", movieMap);
            model.addAttribute("movieId", String.valueOf(movieID));
            model.addAttribute("tmdbId", String.valueOf(movieMap.get("tmdbId")));
            model.addAttribute("recommendTitle", "Có Thể Bạn Thích");
            model.addAttribute("clientSideLoad", false);
            if (!model.containsAttribute("isFavorite")) model.addAttribute("isFavorite", false);

            return "movie/movie-detail";

        } catch (Exception e) {
            System.err.println("❌ Error MovieDetail: " + e.getMessage());
            e.printStackTrace();
        }

        // Fallback an toàn
        return createClientSideFallback(finalIdStr, model);
    }

    // [FIX QUAN TRỌNG] Fallback đầy đủ trường để Thymeleaf không chết
    private String createClientSideFallback(String movieId, Model model) {
        Map<String, Object> movieData = new HashMap<>();
        movieData.put("id", movieId);
        movieData.put("title", "Đang tải dữ liệu...");
        movieData.put("overview", "Vui lòng đợi...");
        movieData.put("backdrop", "/images/placeholder.jpg");
        movieData.put("poster", "/images/placeholder.jpg");
        movieData.put("rating", "0.0");
        
        // THÊM CÁC TRƯỜNG BẮT BUỘC (Tránh lỗi 500 Template)
        movieData.put("budget", 0L);
        movieData.put("revenue", 0L);
        movieData.put("director", "—");
        movieData.put("country", "—");
        movieData.put("language", "—");
        movieData.put("releaseDate", "—");
        
        model.addAttribute("movie", movieData);
        model.addAttribute("movieId", movieId);
        model.addAttribute("tmdbId", "0");
        model.addAttribute("clientSideLoad", true);
        model.addAttribute("trailers", new ArrayList<>());
        model.addAttribute("castList", new ArrayList<>());
        if (!model.containsAttribute("isFavorite")) model.addAttribute("isFavorite", false);
        
        return "movie/movie-detail";
    }
}