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

@Controller
public class MoviePlayerController {

    @Autowired
    private MoviePlayerService moviePlayerService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Value("${app.default.video.url:/video/movie1.mp4}")
    private String defaultVideoUrl;

    @Value("${app.ad.video.url:/video/ad_sample.mp4}")
    private String adVideoUrl;

    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id,
            // CÁCH AN TOÀN NHẤT: Dùng required = false để Spring tiêm NULL thay vì ném lỗi
            @SessionAttribute(name = "user", required = false) UserSessionDto sessionDto,
            Model model) {

        try {
            Movie movie = moviePlayerService.getMovieById(id);
            movie.setUrl(defaultVideoUrl); // Giả định set URL này cho video chính

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

            model.addAttribute("hasAd", hasAd); // Truyền flag có quảng cáo
            model.addAttribute("isVip", isVip); // Truyền trạng thái VIP
            model.addAttribute("movie", movie);
            List<Movie> recommended = moviePlayerService.getRecommendedMovies();
            recommended.removeIf(m -> m.getMovieID() == id);
            model.addAttribute("recommendedMovies", recommended);
            return "movie/player";

        } catch (RuntimeException e) {
            System.err.println("Lỗi: " + e.getMessage());
            return "redirect:/subscriptionPlan";
        }
    }
}
