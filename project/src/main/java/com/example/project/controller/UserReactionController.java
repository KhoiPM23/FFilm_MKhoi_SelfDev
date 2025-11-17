package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam; // [FIX] Thêm import này
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.project.dto.ReactionRequest;
import com.example.project.service.UserReactionService;

@Controller
@RequestMapping("/user-reaction")
public class UserReactionController {

    @Autowired
    private UserReactionService userReactionService;

    @PostMapping("/like")
    @ResponseBody
    // [FIX] Thêm @RequestParam để map đúng dữ liệu từ FormData
    public ResponseEntity<String> handleLike(@RequestParam Integer userId, @RequestParam Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        try {
            userReactionService.likeMovie(reactionRequest);
            return ResponseEntity.ok("reaction recorded");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/dislike")
    @ResponseBody
    // [FIX] Thêm @RequestParam
    public ResponseEntity<String> handleDislike(@RequestParam Integer userId, @RequestParam Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        try {
            userReactionService.dislikeMovie(reactionRequest);
            return ResponseEntity.ok("reaction recorded");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/remove")
    @ResponseBody
    // [FIX] Thêm @RequestParam
    public ResponseEntity<String> handleRemove(@RequestParam Integer userId, @RequestParam Integer tmdbId) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, tmdbId);
        try {
            userReactionService.removeReaction(reactionRequest);
            return ResponseEntity.ok("reaction removed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}