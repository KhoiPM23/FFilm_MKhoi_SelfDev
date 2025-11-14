package com.example.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.dto.ReactionRequest;
import com.example.project.model.UserReaction;
import com.example.project.repository.UserReactionRepository;

@Service
public class UserReactionService {

    @Autowired
    UserReactionRepository userReactionRepository;

    public boolean likeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId()).get();
        userReaction.setIsLike(true);
        userReactionRepository.save(userReaction);
        return true;
    }

    public boolean dislikeMovie(ReactionRequest request) {
        UserReaction userReaction = userReactionRepository
                .findByUser_UserIDAndMovie_TmdbId(request.getUserId(), request.gettmdbId()).get();
        userReaction.setIsLike(false);
        userReactionRepository.save(userReaction);
        return true;
    }

}
