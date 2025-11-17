package com.example.project.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // [QUAN TRỌNG]

import com.example.project.model.Movie;
import com.example.project.repository.MovieRepository;

@Service
public class MoviePlayerService {

    @Autowired
    private MovieRepository movieRepository;

    public List<Movie> getRecommendedMovies() {
        return movieRepository.findTop20ByOrderByReleaseDateDesc();
    }

    /**
     * [FIX] Tìm phim theo ID Khóa chính (PK)
     * Thêm @Transactional để giữ kết nối DB, sửa lỗi LazyInitializationException
     */
    @Transactional
    public Movie getMovieById(int id) {
        // 1. Tìm theo ID nội bộ (movieID) để khớp với link SQL update
        Optional<Movie> movieOtp = movieRepository.findById(id); 

        if (movieOtp.isPresent()) {
            Movie movie = movieOtp.get();
            
            // 2. "Mồi" dữ liệu để Hibernate tải danh sách comment/diễn viên ngay lập tức
            // Điều này ngăn lỗi 500 (Oops error) khi sang trang HTML
            if (movie.getComments() != null) movie.getComments().size();
            if (movie.getPersons() != null) movie.getPersons().size();
            if (movie.getGenres() != null) movie.getGenres().size();
            
            return movie;
        } else {
            throw new RuntimeException("Không tìm thấy phim có ID: " + id);
        }
    }
}