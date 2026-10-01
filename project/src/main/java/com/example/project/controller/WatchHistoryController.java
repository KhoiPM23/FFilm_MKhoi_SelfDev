package com.example.project.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.dto.UserSessionDto;
import com.example.project.dto.WatchHistoryDto;
import com.example.project.service.WatchHistoryService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/history") 
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    public WatchHistoryController(WatchHistoryService watchHistoryService) {
        this.watchHistoryService = watchHistoryService;
    }


    private String extractEmail(UserDetails userDetails, HttpSession session) {
        if (userDetails != null && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
            return userDetails.getUsername();
        }
        if (session != null) {
            Object u = session.getAttribute("user");
            if (u == null) u = session.getAttribute("admin");
            if (u == null) u = session.getAttribute("moderator");
            if (u == null) u = session.getAttribute("contentManager");

            if (u instanceof UserSessionDto) {
                return ((UserSessionDto) u).getEmail();
            } else if (u instanceof com.example.project.model.User) {
                return ((com.example.project.model.User) u).getEmail();
            }
        }
        return null;
    }

    private Integer extractUserId(HttpSession session) {
        if (session != null) {
            Object u = session.getAttribute("user");
            if (u == null) u = session.getAttribute("admin");
            if (u == null) u = session.getAttribute("moderator");
            if (u == null) u = session.getAttribute("contentManager");

            if (u instanceof UserSessionDto) {
                return ((UserSessionDto) u).getId();
            } else if (u instanceof com.example.project.model.User) {
                return ((com.example.project.model.User) u).getUserID();
            }
        }
        return null;
    }

    @PostMapping("/record/{movieId}")
    public ResponseEntity<?> recordWatch(@PathVariable int movieId,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         HttpSession session) {
        String email = extractEmail(userDetails, session);
        if (email == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }
        watchHistoryService.recordWatchHistory(email, movieId);
        return ResponseEntity.ok().build();
    }


    @GetMapping
    public ResponseEntity<Page<WatchHistoryDto>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            @PageableDefault(size = 20) Pageable pageable) {
        String email = extractEmail(userDetails, session);
        if (email == null) {
            return ResponseEntity.status(401).build(); 
        }
        Page<WatchHistoryDto> historyPage = watchHistoryService.getWatchHistory(email, pageable);
        return ResponseEntity.ok(historyPage);
    }

    //Tiến độ xem
    @PostMapping("/update-progress")
    public ResponseEntity<?> updateProgress(
            @RequestParam int movieId,
            @RequestParam Double currentTime,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session) { 
        
        Integer userId = extractUserId(session);
        if (userId != null) {
            watchHistoryService.updateWatchProgress(userId, movieId, currentTime);
            return ResponseEntity.ok().build();
        }

        String email = extractEmail(userDetails, session);
        if (email != null) {
            watchHistoryService.updateWatchProgressByEmail(email, movieId, currentTime);
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.status(401).build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{movieId}")
    public ResponseEntity<?> deleteHistoryItem(
            @PathVariable int movieId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session) {
        String email = extractEmail(userDetails, session);
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        watchHistoryService.deleteWatchHistory(email, movieId);
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Đã xóa khỏi lịch sử xem"));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/clear")
    public ResponseEntity<?> clearAllHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session) {
        String email = extractEmail(userDetails, session);
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        watchHistoryService.clearWatchHistory(email);
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Đã xóa toàn bộ lịch sử xem"));
    }
}