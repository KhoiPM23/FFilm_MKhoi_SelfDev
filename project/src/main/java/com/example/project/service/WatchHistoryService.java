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

    /**
     * Ghi lại hoặc cập nhật lịch sử xem.
     */
    @Transactional
    public void recordWatchHistory(String userEmail, int movieId) {
        // Lấy thông tin user và movie
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found with ID: " + movieId));

        // Kiểm tra xem đã có bản ghi nào chưa
        Optional<WatchHistory> existingHistory = watchHistoryRepository.findByUserAndMovie(user, movie);

        if (existingHistory.isPresent()) {
            // Đã có: Chỉ cần save() để @UpdateTimestamp tự động cập nhật
            WatchHistory history = existingHistory.get();
            watchHistoryRepository.save(history);
        } else {
            // Chưa có: Tạo mới
            WatchHistory newHistory = new WatchHistory(user, movie);
            watchHistoryRepository.save(newHistory);
        }
    }

    /**
     * Lấy lịch sử xem (phân trang) của user và chuyển đổi sang DTO.
     */
    @Transactional(readOnly = true)
    public Page<WatchHistoryDto> getWatchHistory(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));

        // Lấy dữ liệu Page<WatchHistory> từ repository
        Page<WatchHistory> historyPage = watchHistoryRepository.findByUserOrderByLastWatchedAtDesc(user, pageable);

        // Chuyển đổi (map) Page<WatchHistory> sang Page<WatchHistoryDto>
        return historyPage.map(WatchHistoryDto::new);
    }
}