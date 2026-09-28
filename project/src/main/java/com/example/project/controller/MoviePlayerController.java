package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.stereotype.Controller;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.Movie;
import com.example.project.service.MoviePlayerService;
import com.example.project.service.SubscriptionService;
import com.example.project.service.WatchHistoryService;

@Controller
public class MoviePlayerController {

    @Autowired
    private MoviePlayerService moviePlayerService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Value("${app.default.video.url:/video/movie1.mp4}")
    private String defaultVideoUrl;

    @Value("${app.ad.video.url:/video/ad_sample.mp4}")
    private String adVideoUrl;

    @Autowired
    private com.example.project.repository.MovieRepository movieRepository;

    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id,
            // CÁCH AN TOÀN NHẤT: Dùng required = false để Spring tiêm NULL thay vì ném lỗi
            @SessionAttribute(name = "user", required = false) UserSessionDto sessionDto,
            Model model) {

        Movie movie = null;
        try {
            movie = moviePlayerService.getMovieById(id);
        } catch (Exception ex) {
            System.err.println("Fallback getMovieById error: " + ex.getMessage());
            List<Movie> all = movieRepository.findAll();
            if (!all.isEmpty()) {
                movie = all.get(0);
            }
        }

        if (movie == null) {
            movie = new Movie();
            movie.setMovieID(id > 0 ? id : 1);
            movie.setTitle("Phim Mặc Định FFilm");
            movie.setDescription("Video đang phát ở chế độ mặc định để kiểm thử hệ thống.");
            movie.setUrl(defaultVideoUrl);
            movie.setFree(true);
        }

        try {
            String url = movie.getUrl();
            if (url == null || url.isBlank() 
                || url.toUpperCase().contains("CHUA") || url.contains("Chưa") || url.contains("C?P")
                || (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("/"))) {
                movie.setUrl(defaultVideoUrl);
            }

            // 1. Xác định trạng thái VIP của người dùng
            boolean isVip = sessionDto != null && subscriptionService.checkActiveSubscription(sessionDto.getId());

            // 2. Mặc định không quảng cáo
            boolean hasAd = false;

            // 3. Kiểm tra phim TRẢ PHÍ (Ưu tiên)
            if (!movie.isFree() && !isVip) {
                // Phim trả phí VÀ user không phải VIP/chưa đăng nhập
                return "redirect:/subscriptionPlan"; // Bắt buộc mua gói
            }

            // 4. Xử lý phim MIỄN PHÍ
            if (movie.isFree() && !isVip) {
                // Phim miễn phí VÀ user không phải VIP -> Kích hoạt Quảng cáo
                hasAd = true;
                model.addAttribute("adUrl", adVideoUrl);
            }
            // [THÊM MỚI QUAN TRỌNG] Lấy thời gian đã xem để Resume và ghi nhận lịch sử xem
            double startTime = 0.0;
            if (sessionDto != null) {
                try {
                    watchHistoryService.recordWatchHistory(sessionDto.getEmail(), movie.getMovieID());
                } catch (Exception e) {
                    System.err.println("Lỗi ghi nhận lịch sử xem phim server-side: " + e.getMessage());
                }
                try {
                    startTime = watchHistoryService.getWatchedTime(sessionDto.getId(), movie.getMovieID());
                } catch (Exception ignored) {
                    startTime = 0.0;
                }
            }
            model.addAttribute("startTime", startTime); // Truyền xuống HTML

            model.addAttribute("hasAd", hasAd); // Truyền flag có quảng cáo
            model.addAttribute("isVip", isVip); // Truyền trạng thái VIP
            model.addAttribute("movie", movie);
            
            List<Movie> recommended;
            try {
                recommended = moviePlayerService.getRecommendedMovies();
                final int currentMid = movie.getMovieID();
                recommended.removeIf(m -> m.getMovieID() == currentMid);
            } catch (Exception e) {
                recommended = java.util.Collections.emptyList();
            }
            model.addAttribute("recommendedMovies", recommended);
            return "movie/player";

        } catch (Exception e) {
            System.err.println("Lỗi MoviePlayerController: " + e.getMessage());
            // Fallback an toàn tuyệt đối: không để null để không bao giờ hiển thị 404
            movie.setUrl(defaultVideoUrl);
            model.addAttribute("movie", movie);
            model.addAttribute("startTime", 0.0);
            model.addAttribute("hasAd", false);
            model.addAttribute("isVip", false);
            model.addAttribute("recommendedMovies", java.util.Collections.emptyList());
            return "movie/player";
        }
    }
}
