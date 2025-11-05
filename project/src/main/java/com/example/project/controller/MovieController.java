package com.example.project.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.project.dto.MovieListDTO;
import com.example.project.service.MovieService;

@Controller
public class MovieController {
    MovieService movieService;

    @GetMapping("movie/list")
    public String ListMovie(@RequestParam() int movieID, @RequestParam() String title,
            @RequestParam() boolean isFree, @RequestParam() String url, Model model) {
        List<MovieListDTO> movies = movieService.showAllMovies();
        model.addAllAttributes(movies);
        return "list";
    }

}
