package com.example.project.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.dto.ReactionRequest;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.UserReaction;
import com.example.project.repository.UserReactionRepository;
import com.example.project.repository.UserRepository;
// --- SỬA LỖI: CHÚNG TA CẦN CÁI NÀY TRỞ LẠI ---
import com.example.project.repository.MovieRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional; // Thêm import này

@Service
public class UserReactionService {

    @Autowired
    UserReactionRepository userReactionRepository;

    @Autowired
    UserRepository userRepository;

    // --- SỬA LỖI: INJECT MOVIE REPOSITORY ---
    @Autowired
    MovieRepository movieRepository;

    @Transactional
    public boolean likeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId())
                // Hàm orElseGet sẽ gọi createNewReaction nếu không tìm thấy
                .orElseGet(() -> createNewReaction(request.getUserId(), request.gettmdbId()));

        userReaction.setIsLike(true);
        userReaction.setCreatedAt(java.time.LocalDateTime.now());

        userReactionRepository.save(userReaction);
        return true;
    }

    @Transactional
    public boolean dislikeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId())
                // Hàm orElseGet sẽ gọi createNewReaction nếu không tìm thấy
                .orElseGet(() -> createNewReaction(request.getUserId(), request.gettmdbId()));

        userReaction.setIsLike(false);
        userReaction.setCreatedAt(java.time.LocalDateTime.now());

        // Lỗi 500 xảy ra ở dòng này (khi 'userReaction' là đối tượng mới)
        userReactionRepository.save(userReaction);
        return true;
    }

    @Transactional
    public boolean removeReaction(ReactionRequest request) {
        Optional<UserReaction> userReactionOpt = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId());

        if (userReactionOpt.isPresent()) {
            userReactionRepository.delete(userReactionOpt.get());
        }
        return true;
    }

    // --- SỬA LỖI: HÀM NÀY ĐÃ ĐƯỢC SỬA LẠI ĐÚNG ---
    private UserReaction createNewReaction(Integer userId, Integer tmdbId) {

        // 1. Lấy User (Đã đúng)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // 2. PHẢI LẤY MOVIE TỪ DATABASE
        // Chúng ta phải lấy đối tượng 'Movie' mà Hibernate quản lý.
        // (Giả sử bạn có 'findByTmdbId' trong MovieRepository)
        Movie movie = movieRepository.findByTmdbId(tmdbId)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found with tmdbId: " + tmdbId));

        // 3. Tạo Reaction mới
        UserReaction newReaction = new UserReaction();
        newReaction.setUser(user);
        newReaction.setMovie(movie); // Gán đối tượng 'Movie' đã được quản lý

        return newReaction;
    }
}