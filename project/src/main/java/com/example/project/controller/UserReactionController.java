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

    private UserSessionDto getUserSession(HttpSession session) {
        if (session == null) return null;
        if (session.getAttribute("user") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("user");
        }
        if (session.getAttribute("admin") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("admin");
        }
        if (session.getAttribute("moderator") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("moderator");
        }
        if (session.getAttribute("contentManager") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("contentManager");
        }
        return null;
    }

    private Integer resolveMovieId(Integer movieID, Integer movieIdParam) {
        return (movieID != null && movieID > 0) ? movieID : movieIdParam;
    }

    @PostMapping("/like")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction(
            @org.springframework.web.bind.annotation.RequestParam(value = "movieID", required = false) Integer movieID,
            @org.springframework.web.bind.annotation.RequestParam(value = "movieId", required = false) Integer movieIdParam,
            HttpSession session) {
        UserSessionDto userSession = getUserSession(session);
        if (userSession == null) return ResponseEntity.status(401).body("Unauthorized");

        Integer resolvedMovieId = resolveMovieId(movieID, movieIdParam);
        if (resolvedMovieId == null || resolvedMovieId <= 0) {
            return ResponseEntity.badRequest().body("movieID is required");
        }

        ReactionRequest reactionRequest = new ReactionRequest(userSession.getId(), resolvedMovieId);
        userReactionService.likeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/dislike")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction2(
            @org.springframework.web.bind.annotation.RequestParam(value = "movieID", required = false) Integer movieID,
            @org.springframework.web.bind.annotation.RequestParam(value = "movieId", required = false) Integer movieIdParam,
            HttpSession session) {
        UserSessionDto userSession = getUserSession(session);
        if (userSession == null) return ResponseEntity.status(401).body("Unauthorized");

        Integer resolvedMovieId = resolveMovieId(movieID, movieIdParam);
        if (resolvedMovieId == null || resolvedMovieId <= 0) {
            return ResponseEntity.badRequest().body("movieID is required");
        }

        ReactionRequest reactionRequest = new ReactionRequest(userSession.getId(), resolvedMovieId);
        userReactionService.dislikeMovie(reactionRequest);
        return ResponseEntity.ok("reaction recorded");
    }

    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<String> handleUserReaction3(
            @org.springframework.web.bind.annotation.RequestParam(value = "movieID", required = false) Integer movieID,
            @org.springframework.web.bind.annotation.RequestParam(value = "movieId", required = false) Integer movieIdParam,
            HttpSession session) {
        UserSessionDto userSession = getUserSession(session);
        if (userSession == null) return ResponseEntity.status(401).body("Unauthorized");

        Integer resolvedMovieId = resolveMovieId(movieID, movieIdParam);
        if (resolvedMovieId == null || resolvedMovieId <= 0) {
            return ResponseEntity.badRequest().body("movieID is required");
        }

        ReactionRequest reactionRequest = new ReactionRequest(userSession.getId(), resolvedMovieId);
        userReactionService.removeReaction(reactionRequest);
        return ResponseEntity.ok("reaction removed");
    }

    @GetMapping("/engagement/{movieId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMovieEngagement(
            @PathVariable Integer movieId,
            HttpSession session) {
        if (movieId == null || movieId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("totalLikes", 0L, "userAction", "none"));
        }

        Integer userId = null;
        UserSessionDto userSession = getUserSession(session);
        if (userSession != null) {
            userId = userSession.getId();
        }

        Map<String, Object> engagementData = userReactionService.getMovieEngagement(userId, movieId);
        return ResponseEntity.ok(engagementData);
    }
}
