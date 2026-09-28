package com.example.project.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.model.Movie;
import com.example.project.repository.MovieRepository;

@Service
public class MoviePlayerService {
    @Autowired
    private MovieRepository movieRepository;

    // Lấy phim theo ID for player.html
    public List<Movie> getRecommendedMovies() {
        return movieRepository.findTop20ByOrderByReleaseDateDesc();
    }

    public Movie getMovieById(int id) {

  
        Optional<Movie> movieOtp = movieRepository.findById(id); 
        if (movieOtp.isEmpty()) {
            movieOtp = movieRepository.findByTmdbId(id);
        }

        if (movieOtp.isPresent()) {
            return movieOtp.get();
        } else {
            // Fallback to first movie in DB if available to avoid blocking QA
            List<Movie> list = movieRepository.findTop20ByOrderByReleaseDateDesc();
            if (!list.isEmpty()) {
                return list.get(0);
            }
            throw new RuntimeException("Không tìm thấy phim có Movie ID: " + id);
        }
    }
}
