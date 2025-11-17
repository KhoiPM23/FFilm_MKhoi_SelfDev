package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.stereotype.Controller;
import com.example.project.model.Movie;
import com.example.project.service.MoviePlayerService;
import com.example.project.service.MovieService;

@Controller
public class MoviePlayerController {

    @Autowired
    private MoviePlayerService moviePlayerService;

    // lấy dữ liệu cho lớp player of Nguyên
    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id, Model model) {
        try {
            // [FIX] Gọi hàm tìm theo PK (ID nội bộ) thay vì TMDB ID
            Movie movie = moviePlayerService.getMovieById(id);
            model.addAttribute("movie", movie);

            // Lấy list đề xuất (trừ phim đang xem)
            List<Movie> recommendedMovies = moviePlayerService.getRecommendedMovies();
            // So sánh theo movieID để loại bỏ chính xác
            recommendedMovies.removeIf(m -> m.getMovieID() == id);
            
            model.addAttribute("recommendedMovies", recommendedMovies);
            return "movie/player";
        } catch (RuntimeException e) {
            System.err.println("Lỗi Player: " + e.getMessage());
            return "redirect:/";
        }
    }
}
