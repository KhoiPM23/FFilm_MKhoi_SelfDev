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
import com.example.project.service.MovieService;
import com.example.project.service.SubscriptionService;

@Controller
public class MoviePlayerController {

    @Autowired
    private MoviePlayerService moviePlayerService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Value("${app.default.video.url:/video/movie1.mp4}")
    private String defaultVideoUrl;

    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id,
            // CÁCH AN TOÀN NHẤT: Dùng required = false để Spring tiêm NULL thay vì ném lỗi
            @SessionAttribute(name = "user", required = false) UserSessionDto sessionDto,
            Model model) {

        try {
            Movie movie = moviePlayerService.getMovieById(id);
            movie.setUrl(defaultVideoUrl);

            // [LOGIC BẢO VỆ] Kiểm tra nếu phim trả phí VÀ người dùng chưa đăng nhập
            if (!movie.isFree() && sessionDto == null) {
                return "redirect:/login"; // Redirect nếu chưa login
            }
            if (!movie.isFree() && sessionDto != null
                    && !subscriptionService.checkActiveSubscription(sessionDto.getId())) {
                return "redirect:/subscriptionPlan";
            }

            // ... (Tiếp tục logic xem phim)
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
