package com.example.project.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.WatchHistory;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    /**
     * Tìm kiếm một bản ghi lịch sử dựa trên User và Movie.
     * Dùng để kiểm tra xem user đã xem phim này chưa.
     */
    Optional<WatchHistory> findByUserAndMovie(User user, Movie movie);

    /**
     * Lấy danh sách lịch sử xem của một user,
     * sắp xếp theo thời gian xem gần nhất (lastWatchedAt) giảm dần (DESC).
     * Hỗ trợ phân trang (Pageable).
     */
    Page<WatchHistory> findByUserOrderByLastWatchedAtDesc(User user, Pageable pageable);
}