package com.example.project.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.project.dto.ReactionRequest;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.UserReaction;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserReactionRepository;
import com.example.project.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class UserReactionService {

    @Autowired
    UserReactionRepository userReactionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MovieRepository movieRepository;

    @Transactional
    public boolean likeMovie(ReactionRequest request) {
        UserReaction reaction = getOrCreateReaction(request.getUserId(), request.gettmdbId());
        reaction.setIsLike(true);
        reaction.setCreatedAt(LocalDateTime.now());
        userReactionRepository.save(reaction);
        return true;
    }

    @Transactional
    public boolean dislikeMovie(ReactionRequest request) {
        UserReaction reaction = getOrCreateReaction(request.getUserId(), request.gettmdbId());
        reaction.setIsLike(false);
        reaction.setCreatedAt(LocalDateTime.now());
        userReactionRepository.save(reaction);
        return true;
    }

    @Transactional
    public boolean removeReaction(ReactionRequest request) {
        Optional<UserReaction> reactionOpt = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId());
        
        reactionOpt.ifPresent(userReaction -> userReactionRepository.delete(userReaction));
        return true;
    }

    // Hàm helper để tìm hoặc tạo mới Reaction (Xử lý logic gọn gàng ở đây)
    private UserReaction getOrCreateReaction(Integer userId, Integer tmdbId) {
        return userReactionRepository.findByUser_UserIDAndMovie_TmdbId(userId, tmdbId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
                    
                    Movie movie = movieRepository.findByTmdbId(tmdbId)
                            .orElseThrow(() -> new EntityNotFoundException("Movie not found with tmdbId: " + tmdbId));
                    
                    // Khởi tạo mới mặc định
                    UserReaction newReaction = new UserReaction();
                    newReaction.setUser(user);
                    newReaction.setMovie(movie);
                    newReaction.setIsLike(true); // Giá trị tạm
                    return newReaction;
                });
    }
}