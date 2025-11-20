package com.example.project.service;

import java.util.HashMap;
import java.util.Map;
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
import jakarta.transaction.Transactional;

@Service
public class UserReactionService {

    @Autowired
    UserReactionRepository userReactionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MovieRepository movieRepository;

    @Transactional
    public Map<String, Object> getMovieEngagement(Integer userId, Integer movieID) {
        Map<String, Object> result = new HashMap<>();

        // 1. Lấy Tổng số Like từ Database (Khắc phục Vấn đề 2)
        Long totalLikes = userReactionRepository.countLikesByMovieID(movieID);
        result.put("totalLikes", totalLikes);

        // 2. Lấy Trạng thái của Người dùng hiện tại (Khắc phục Vấn đề 1)
        String userAction = "none"; // 'like', 'dislike', 'none'
        if (userId != null) {
            Optional<UserReaction> reactionOpt = userReactionRepository
                    .findByUser_UserIDAndMovie_MovieID(userId, movieID);

            if (reactionOpt.isPresent()) {
                UserReaction reaction = reactionOpt.get();
                // isLike là Boolean, cần check null an toàn nếu cần
                userAction = Boolean.TRUE.equals(reaction.getIsLike()) ? "like" : "dislike";
            }
        }
        result.put("userAction", userAction);

        return result;
    }

    @Transactional
    public boolean likeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_MovieID(request.getUserId(), request.getMovieID())
                .orElseGet(() -> createNewReaction(request.getUserId(), request.getMovieID()));

        userReaction.setIsLike(true);
        userReaction.setCreatedAt(java.time.LocalDateTime.now());

        userReactionRepository.save(userReaction);
        return true;
    }

    @Transactional
    public boolean dislikeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_MovieID(request.getUserId(), request.getMovieID())
                .orElseGet(() -> createNewReaction(request.getUserId(), request.getMovieID()));

        userReaction.setIsLike(false);
        userReaction.setCreatedAt(java.time.LocalDateTime.now());

        userReactionRepository.save(userReaction);
        return true;
    }

    @Transactional
    public boolean removeReaction(ReactionRequest request) {
        Optional<UserReaction> userReactionOpt = userReactionRepository
                .findByUser_UserIDAndMovie_MovieID(request.getUserId(), request.getMovieID());
        if (userReactionOpt.isPresent()) {
            userReactionRepository.delete(userReactionOpt.get());
        }
        return true;
    }

    private UserReaction createNewReaction(Integer userId, Integer movieID) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Movie movie = movieRepository.findByMovieID(movieID)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found with movieID: " + movieID));

        UserReaction newReaction = new UserReaction();
        newReaction.setUser(user);
        newReaction.setMovie(movie);

        return newReaction;
    }

}