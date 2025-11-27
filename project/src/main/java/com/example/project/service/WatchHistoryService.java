package com.example.project.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.project.dto.WatchHistoryDto;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.WatchHistory;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;
import com.example.project.repository.WatchHistoryRepository;

@Service
public class WatchHistoryService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;

    public WatchHistoryService(WatchHistoryRepository watchHistoryRepository,
                               UserRepository userRepository,
                               MovieRepository movieRepository) {
        this.watchHistoryRepository = watchHistoryRepository;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
    }

    @Transactional
    public void recordWatchHistory(String userEmail, int movieId) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found with ID: " + movieId));

        Optional<WatchHistory> existingHistory = watchHistoryRepository.findByUserAndMovie(user, movie);

        if (existingHistory.isPresent()) {
            WatchHistory history = existingHistory.get();

            history.setLastWatchedAt(java.time.LocalDateTime.now());
            watchHistoryRepository.save(history);
        } else {

            WatchHistory newHistory = new WatchHistory(user, movie);
            watchHistoryRepository.save(newHistory);
        }
    }


    @Transactional(readOnly = true)
    public Page<WatchHistoryDto> getWatchHistory(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));

        Page<WatchHistory> historyPage = watchHistoryRepository.findByUserOrderByLastWatchedAtDesc(user, pageable);

        return historyPage.map(WatchHistoryDto::new);
    }


    @Transactional
    public void updateWatchProgress(Integer userId, int movieId, Double currentTime) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found"));

        Optional<WatchHistory> existingHistory = watchHistoryRepository.findByUserAndMovie(user, movie);

        if (existingHistory.isPresent()) {
            WatchHistory history = existingHistory.get();
            history.setLastWatchedAt(java.time.LocalDateTime.now());

            if (currentTime != null) {
                history.setCurrentTime(currentTime);
            }
            watchHistoryRepository.save(history);
        } else {
            WatchHistory newHistory = new WatchHistory(user, movie);
            if (currentTime != null) {
                newHistory.setCurrentTime(currentTime);
            }
            watchHistoryRepository.save(newHistory);
        }
    }

    public Double getWatchedTime(Integer userId, int movieId) {

        User user = new User(); user.setUserID(userId);
        Movie movie = new Movie(); movie.setMovieID(movieId);
        
        return watchHistoryRepository.findByUserAndMovie(user, movie)
                .map(WatchHistory::getCurrentTime)
                .orElse(0.0);
    }
}