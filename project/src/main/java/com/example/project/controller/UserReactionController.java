package com.example.project.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import com.example.project.dto.ReactionRequest;
import com.example.project.dto.UserSessionDto;
import com.example.project.service.UserReactionService;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<String> handleUserReaction(Integer userId, Integer movieID) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, movieID);
        userReactionService.likeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/dislike")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction2(Integer userId, Integer movieID) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, movieID);
        userReactionService.dislikeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction3(Integer userId, Integer movieID) {
        ReactionRequest reactionRequest = new ReactionRequest(userId, movieID);
        userReactionService.removeReaction(reactionRequest);
        return ResponseEntity.ok("reaction removed");
    }

    @GetMapping("/engagement/{movieId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMovieEngagement(
            @PathVariable Integer movieId,
            HttpSession session) {

        Integer userId = null;
        // Lấy User ID từ session để truyền vào service
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        if (userSession != null) {
            userId = userSession.getId();
        }

        // Gọi service để lấy dữ liệu
        Map<String, Object> engagementData = userReactionService.getMovieEngagement(userId, movieId);
        return ResponseEntity.ok(engagementData);
    }

}
