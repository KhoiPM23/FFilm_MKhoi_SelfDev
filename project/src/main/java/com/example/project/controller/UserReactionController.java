package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import com.example.project.dto.ReactionRequest;
import com.example.project.service.UserReactionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/user-reaction")
public class UserReactionController {
    @Autowired
    private UserReactionService userReactionService;

    @PostMapping("/like")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction(Integer userId, Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        userReactionService.likeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/dislike")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction2(Integer userId, Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        userReactionService.dislikeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction3(Integer userId, Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        userReactionService.removeReaction(reactionRequest);
        return ResponseEntity.ok("reaction removed");
    }

}
