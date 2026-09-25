package com.example.project.service;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.project.model.Movie;
import com.example.project.model.Review;
import com.example.project.model.User;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.ReviewRepository;
import com.example.project.repository.UserRepository;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Lấy tóm tắt đánh giá (Community Rating vs TMDB Rating)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRatingSummary(int movieId, Integer currentUserId) {
        Map<String, Object> result = new HashMap<>();

        Movie movie = movieRepository.findById(movieId).orElse(null);
        if (movie == null) {
            result.put("success", false);
            result.put("message", "Phim không tồn tại");
            return result;
        }

        double communityRating = 0.0;
        long ratingCount = 0;

        List<Object[]> stats = reviewRepository.getRatingStatsByMovieId(movieId);
        if (stats != null && !stats.isEmpty()) {
            Object[] row = stats.get(0);
            if (row != null && row.length >= 2 && row[0] != null) {
                communityRating = Math.round(((Number) row[0]).doubleValue() * 10.0) / 10.0;
                ratingCount = ((Number) row[1]).longValue();
            }
        }

        Integer userRating = null;
        if (currentUserId != null) {
            Optional<Review> userReview = reviewRepository.findByUser_UserIDAndMovie_MovieID(currentUserId, movieId);
            if (userReview.isPresent()) {
                userRating = userReview.get().getRating();
            }
        }

        result.put("success", true);
        result.put("movieId", movieId);
        result.put("communityRating", communityRating);
        result.put("ratingCount", ratingCount);
        result.put("userRating", userRating);
        result.put("tmdbRating", (double) movie.getRating());
        result.put("tmdbVoteCount", movie.getVoteCount() != null ? movie.getVoteCount() : 0);

        return result;
    }

    /**
     * Gửi hoặc cập nhật đánh giá (1-5 sao)
     */
    @Transactional
    public Map<String, Object> submitRating(int movieId, int userId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Đánh giá phải từ 1 đến 5 sao");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Phim không tồn tại"));

        Optional<Review> existing = reviewRepository.findByUser_UserIDAndMovie_MovieID(userId, movieId);
        Review review;
        if (existing.isPresent()) {
            review = existing.get();
            review.setRating(rating);
            review.setCreateAt(new Date());
            log.info("User {} updated rating for movie {} to {}", userId, movieId, rating);
        } else {
            review = new Review(rating, new Date(), user, movie);
            log.info("User {} submitted new rating for movie {}: {}", userId, movieId, rating);
        }
        reviewRepository.save(review);

        return getRatingSummary(movieId, userId);
    }

    /**
     * Hủy đánh giá của người dùng
     */
    @Transactional
    public Map<String, Object> removeRating(int movieId, int userId) {
        reviewRepository.deleteByUser_UserIDAndMovie_MovieID(userId, movieId);
        log.info("User {} removed rating for movie {}", userId, movieId);
        return getRatingSummary(movieId, userId);
    }

    /**
     * Lấy danh sách rating của các user đã bình luận trên phim này
     * Trả về Map<userId, rating> để gắn huy hiệu sao bên cạnh avatar bình luận
     */
    @Transactional(readOnly = true)
    public Map<Integer, Integer> getUserRatingsMapByMovie(int movieId) {
        List<Review> reviews = reviewRepository.findByMovie_MovieIDOrderByCreateAtDesc(movieId);
        Map<Integer, Integer> map = new HashMap<>();
        for (Review r : reviews) {
            if (r.getUser() != null) {
                map.put(r.getUser().getUserID(), r.getRating());
            }
        }
        return map;
    }
}
