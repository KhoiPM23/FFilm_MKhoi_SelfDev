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