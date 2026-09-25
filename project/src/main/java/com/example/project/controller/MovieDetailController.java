package com.example.project.controller;

import com.example.project.service.MovieService;

import com.example.project.dto.UserSessionDto; // [THÊM] Import UserSessionDto
import com.example.project.service.UserFavoriteService;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.project.service.SubscriptionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class MovieDetailController {

    private static final Logger log = LoggerFactory.getLogger(MovieDetailController.class);

    @Autowired
    private MovieService movieService;

    @Autowired
    private UserFavoriteService userFavoriteService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private com.example.project.service.ReviewService reviewService;

    @GetMapping({ "/movie/detail/{id}", "/movie/detail" })
    public String movieDetail(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String movieId,
            Model model, HttpSession session) {
        Object userObj = session.getAttribute("user");
        UserSessionDto userSession = null;
        if (userObj instanceof UserSessionDto) {
            userSession = (UserSessionDto) userObj;
        } else if (userObj instanceof com.example.project.model.User) {
            com.example.project.model.User u = (com.example.project.model.User) userObj;
            userSession = new UserSessionDto(u.getUserID(), u.getUserName(), u.getEmail(), u.getRole());
        }

        String finalIdStr = (id != null && !id.isEmpty()) ? id : movieId;
        if (finalIdStr == null || finalIdStr.isEmpty()) return "redirect:/";

        try {
            int movieID = Integer.parseInt(finalIdStr);

            // [QUAN TRỌNG] Gọi hàm Service mới để lấy Map chi tiết (Đã bao gồm castList chuẩn Role)
            Map<String, Object> movieMap = movieService.getMovieDetailMap(movieID);

            // 1. Bóc tách Cast List (Service đã xử lý Role/Character từ DB)
            List<?> castList = new ArrayList<>();
            if (movieMap.containsKey("castList")) {
                castList = (List<?>) movieMap.get("castList");
            }
            model.addAttribute("castList", castList);

            // 2. Xử lý Trailer (Service đã xử lý)
            List<Map<String, Object>> trailers = new ArrayList<>();
            String tKey = (String) movieMap.get("trailerKey");
            if (tKey != null && !tKey.isEmpty()) {
                Map<String, Object> t = new HashMap<>();
                t.put("key", tKey);
                t.put("name", "Trailer Chính Thức");
                trailers.add(t);
            }
            model.addAttribute("trailers", trailers);

            // 3. Xử lý User Status (Yêu thích / VIP)
            boolean isFavorite = false;
            boolean isVip = false;
            if (userSession != null) {
                isFavorite = userFavoriteService.isFavorite(userSession.getId(), movieID);
                isVip = subscriptionService.checkActiveSubscription(userSession.getId());
            }
            model.addAttribute("isFavorite", isFavorite);
            model.addAttribute("isVip", isVip);

            // 4. Xử lý Rating & Đánh giá cộng đồng
            Integer currentUserId = (userSession != null) ? userSession.getId() : null;
            Map<String, Object> ratingSummary = reviewService.getRatingSummary(movieID, currentUserId);
            model.addAttribute("ratingSummary", ratingSummary);
            model.addAttribute("communityRating", ratingSummary.get("communityRating"));
            model.addAttribute("ratingCount", ratingSummary.get("ratingCount"));
            model.addAttribute("userRating", ratingSummary.get("userRating"));

            // 5. Gán các Attribute còn lại
            model.addAttribute("movie", movieMap);
            model.addAttribute("movieId", String.valueOf(movieID));
            model.addAttribute("tmdbId", String.valueOf(movieMap.get("tmdbId")));
            model.addAttribute("recommendTitle", "Có Thể Bạn Thích"); // Tiêu đề cho Carousel Gợi ý
            model.addAttribute("clientSideLoad", false);

            return "movie/movie-detail";

        } catch (Exception e) {
            log.error("Failed to load movie detail for id {}", finalIdStr, e);
            // Fallback nếu lỗi
            return createClientSideFallback(finalIdStr, model);
        }
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
        model.addAttribute("communityRating", 0.0);
        model.addAttribute("ratingCount", 0L);
        model.addAttribute("userRating", null);
        if (!model.containsAttribute("isFavorite"))
            model.addAttribute("isFavorite", false);

        return "movie/movie-detail";
    }
}