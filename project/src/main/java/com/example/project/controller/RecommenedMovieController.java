package com.example.project.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.example.project.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.Movie;

@Controller
public class RecommenedMovieController {
    @Autowired
    private RecommendationService recommendationService;

    @GetMapping("/recommnended")
    public String getMethodName(@SessionAttribute("user") UserSessionDto userSession, Model model) {
        List<Movie> recommendations = recommendationService.getRecommendations(userSession.getId());
        model.addAttribute("recommendations", recommendations);
        return "movie/recommended-movie";
    }

}
