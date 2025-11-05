package com.example.project.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.project.dto.MovieListDTO;
import com.example.project.repository.MovieRepository;

@Service
public class MovieService {
    private MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<MovieListDTO> getMovieByCategory(int categoryId) {
        return movieRepository.findByCategory(Sort.by(Sort.Direction.DESC, "movieID")).stream()
                .map(movie -> new MovieListDTO(
                        movie.getMovieID(),
                        movie.getTitle(),
                        movie.isFree(),
                        movie.getUrl() != null ? movie.getUrl() : "/images/default_poster.jpg" // fallback image
                ))
                .toList();
    }
}
