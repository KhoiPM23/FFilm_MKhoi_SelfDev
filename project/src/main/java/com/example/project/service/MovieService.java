package com.example.project.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.project.dto.MovieListDTO;
import com.example.project.model.Category;
import com.example.project.model.Movie;
import com.example.project.repository.CategoryRepository;
import com.example.project.repository.MovieRepository;

@Service
public class MovieService {
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<MovieListDTO> getMovieByCategory(int categoryId, int page, int size) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("releaseDate").descending());
        Page<Movie> moviePage = movieRepository.findByCategoriesContaining(category, pageable);
        return moviePage.getContent().stream()
                .map(movie -> new MovieListDTO(
                        movie.getMovieID(),
                        movie.getTitle(),
                        movie.isFree(),
                        movie.getUrl() != null ? movie.getUrl() : "/images/default_poster.jpg"))
                .collect(Collectors.toList());
    }

}
