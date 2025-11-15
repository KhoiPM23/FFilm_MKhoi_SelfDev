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
import com.example.project.repository.MovieRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class UserReactionService {

    @Autowired
    UserReactionRepository userReactionRepository;

    @Autowired
    UserRepository userRepository;
    @Autowired
    MovieRepository movieRepository;

    public boolean likeMovie(ReactionRequest request) {
        // 1. TÌM hoặc TẠO MỚI (Đây là nơi dùng Optional)
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId())
                .orElseGet(() -> createNewReaction(request.getUserId(), request.gettmdbId())); // Dùng hàm trợ giúp để
                                                                                               // tạo mới

        // 2. SỬA ĐỔI
        userReaction.setIsLike(true);
        userReaction.setCreatedAt(java.time.LocalDateTime.now());

        // 3. LƯU (Không có Optional ở đây)
        userReactionRepository.save(userReaction);

        return true;
    }

    // Bạn cần thêm hàm này vào Service
    private UserReaction createNewReaction(Integer userId, Integer tmdbId) {
        // Bạn cần inject UserRepository và MovieRepository vào Service này
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Movie movie = movieRepository.findByTmdbId(tmdbId)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + tmdbId));

        UserReaction newReaction = new UserReaction();
        newReaction.setUser(user);
        newReaction.setMovie(movie);
        return newReaction;
    }

    public boolean dislikeMovie(ReactionRequest request) {
        // 1. TÌM hoặc TẠO MỚI (Đây là nơi dùng Optional)
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId())
                .orElseGet(() -> createNewReaction(request.getUserId(), request.gettmdbId())); // Dùng hàm trợ giúp để
                                                                                               // tạo mới

        // 2. SỬA ĐỔI
        userReaction.setIsLike(false);

        // 3. LƯU (Không có Optional ở đây)
        userReactionRepository.save(userReaction);

        return true;
    }

    public boolean removeReaction(ReactionRequest request) {
        // 1. TÌM
        Optional<UserReaction> userReactionOpt = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId());

        // 2. NẾU TỒN TẠI -> XÓA
        if (userReactionOpt.isPresent()) {
            userReactionRepository.delete(userReactionOpt.get());
        }
        // Nếu không tồn tại thì không làm gì cả

        return true;
    }

}
