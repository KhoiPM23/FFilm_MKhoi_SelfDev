package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.default.video.url:/video/movie1.mp4}")
    private String defaultVideoUrl;

    @GetMapping("/movie/player/{id}")
    public String watchMovie(@PathVariable("id") int id, Model model) {
        try {
            Movie movie = moviePlayerService.getMovieById(id);

            // [FIX CRITICAL]: DÒNG BẠN CẦN THÊM VÀO ĐÂY
            movie.setUrl(defaultVideoUrl);

            model.addAttribute("movie", movie);

            System.out.println("Video URL: " + movie.getUrl());
            List<Movie> recommendedMovies = moviePlayerService.getRecommendedMovies();
            recommendedMovies.removeIf(m -> m.getMovieID() == id);
            model.addAttribute("recommendedMovies", recommendedMovies);
            return "movie/player";
        } catch (RuntimeException e) {
            System.err.println("Lỗi: " + e.getMessage());
            return "redirect:/";
        }
    }
}
