package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.project.dto.MovieListDTO;
import com.example.project.model.Category;
import com.example.project.service.CategoryService;
import com.example.project.service.MovieService;

@Controller
public class MovieController {

    @Autowired
    private final MovieService movieService;
    @Autowired
    private final CategoryService categoryService;

    public MovieController(MovieService movieService, CategoryService categoryService) {
        this.movieService = movieService;
        this.categoryService = categoryService;
    }

    @GetMapping("/movie/category/{name}")
    public String listMovieByCategory(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Category category = categoryService.getCategoryByName(name)
                .orElseThrow(() -> new RuntimeException("Category not found: " + name));

        List<MovieListDTO> movies = movieService.getMovieByCategory(category.getCategoryID(), page, size);

        model.addAttribute("categoryName", category.getName());
        model.addAttribute("movies", movies);
        model.addAttribute("currentPage", page);
        model.addAttribute("hasMore", movies.size() == size);

        return "service/movie-category-list";
    }
}