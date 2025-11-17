package com.example.project.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Quan trọng cho thao tác Delete

import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.dto.MovieFavorite;
import com.example.project.model.Movie;
import com.example.project.model.UserFavorite;
import com.example.project.model.UserFavoriteId; // Import Composite Key
import com.example.project.repository.FavoriteRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserFavoriteService {

    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;

    /**
     * Logic Toggle: 
     * - Tìm phim theo TMDB ID -> Lấy Internal ID.
     * - Nếu đã tim -> Xóa -> Trả về "removed"
     * - Chưa tim -> Thêm -> Trả về "added"
     */
    @Transactional 
    public String toggleFavorite(AddUserFavoriteRequest req) {
        // 1. Kiểm tra User tồn tại
        if (!userRepository.existsById(req.getUserID())) {
            return "error";
        }

        // 2. Tìm Movie Entity từ TMDB ID
        Optional<Movie> movieOpt = movieRepository.findByTmdbId(req.getTmdbId());
        if (movieOpt.isEmpty()) {
            return "error"; // Phim chưa được sync vào DB
        }

        int internalMovieId = movieOpt.get().getMovieID();

        // 3. Tạo Composite Key để kiểm tra
        UserFavoriteId key = new UserFavoriteId(internalMovieId, req.getUserID());

        if (favoriteRepository.existsById(key)) {
            // CASE: Đã yêu thích -> XÓA
            favoriteRepository.deleteById(key);
            return "removed";
        } else {
            // CASE: Chưa yêu thích -> THÊM MỚI
            UserFavorite uf = new UserFavorite();
            uf.setUserID(req.getUserID());
            uf.setMovieID(internalMovieId); // Lưu Internal ID
            uf.setCreateAt(req.getCreateAt());
            
            favoriteRepository.save(uf);
            return "added";
        }
    }

    // Hàm hiển thị danh sách (Giữ nguyên)
    public Page<MovieFavorite> showFavoriteList(Integer userID, Integer page, Integer size) {
        if (userID == null) throw new IllegalArgumentException("userId is required");
        Pageable pageable = PageRequest.of(Math.max(0, page), size > 0 ? size : 10);
        return favoriteRepository.findMoviesByUserID(userID, pageable);
    }
}