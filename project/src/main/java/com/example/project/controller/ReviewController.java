package com.example.project.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.User;
import com.example.project.service.ReviewService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    /**
     * Lấy tóm tắt đánh giá của phim (Community Rating, Count, User Rating, TMDB Rating)
     * GET /api/reviews/movie/{movieId}
     */
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<Map<String, Object>> getMovieRatingSummary(
            @PathVariable int movieId,
            HttpSession session) {
        try {
            Integer userId = extractUserId(session);
            Map<String, Object> summary = reviewService.getRatingSummary(movieId, userId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error retrieving rating summary for movie {}", movieId, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi khi lấy thông tin đánh giá: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Gửi đánh giá cho phim (1 - 5 sao)
     * POST /api/reviews
     * Body: { "movieId": 1, "rating": 5 }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitRating(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Integer userId = extractUserId(session);
        if (userId == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Bạn cần đăng nhập để đánh giá phim");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        try {
            Object movieIdObj = payload.get("movieId");
            Object ratingObj = payload.get("rating");

            if (movieIdObj == null || ratingObj == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Thiếu movieId hoặc rating");
                return ResponseEntity.badRequest().body(err);
            }

            int movieId = ((Number) movieIdObj).intValue();
            int rating = ((Number) ratingObj).intValue();

            if (rating < 1 || rating > 5) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Đánh giá không hợp lệ, vui lòng chọn từ 1 đến 5 sao");
                return ResponseEntity.badRequest().body(err);
            }

            Map<String, Object> updatedSummary = reviewService.submitRating(movieId, userId, rating);
            updatedSummary.put("message", "Đánh giá phim thành công!");
            return ResponseEntity.ok(updatedSummary);

        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            log.error("Error submitting rating by user {}", userId, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi hệ thống khi lưu đánh giá");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Hủy đánh giá của người dùng
     * DELETE /api/reviews/movie/{movieId}
     */
    @DeleteMapping("/movie/{movieId}")
    public ResponseEntity<Map<String, Object>> removeRating(
            @PathVariable int movieId,
            HttpSession session) {
        Integer userId = extractUserId(session);
        if (userId == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Bạn cần đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        try {
            Map<String, Object> updatedSummary = reviewService.removeRating(movieId, userId);
            updatedSummary.put("message", "Đã xóa đánh giá của bạn");
            return ResponseEntity.ok(updatedSummary);
        } catch (Exception e) {
            log.error("Error removing rating for movie {} by user {}", movieId, userId, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi khi xóa đánh giá: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Lấy map rating của các user đã đánh giá phim này
     * GET /api/reviews/movie/{movieId}/user-map
     */
    @GetMapping("/movie/{movieId}/user-map")
    public ResponseEntity<Map<Integer, Integer>> getUserRatingsMap(@PathVariable int movieId) {
        return ResponseEntity.ok(reviewService.getUserRatingsMapByMovie(movieId));
    }

    private Integer extractUserId(HttpSession session) {
        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            return null;
        }
        if (userObj instanceof UserSessionDto) {
            return ((UserSessionDto) userObj).getId();
        }
        if (userObj instanceof User) {
            return ((User) userObj).getUserID();
        }
        return null;
    }
}
