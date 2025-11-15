package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.example.project.dto.ReactionRequest;
import com.example.project.service.UserReactionService;

@Controller
public class UserReactionController {
    @Autowired
    private UserReactionService userReactionService;

    public void handleUserReaction(Long userId, Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId.intValue(), tmdbId);
        userReactionService.likeMovie(reactionRequest);
    }

}
