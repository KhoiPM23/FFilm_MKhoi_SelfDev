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


    @PostMapping("/record/{movieId}")
    public ResponseEntity<?> recordWatch(@PathVariable int movieId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }
        watchHistoryService.recordWatchHistory(userDetails.getUsername(), movieId);
        return ResponseEntity.ok().build();
    }


    @GetMapping
    public ResponseEntity<Page<WatchHistoryDto>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build(); 
        }
        Page<WatchHistoryDto> historyPage = watchHistoryService.getWatchHistory(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(historyPage);
    }

    //Tiến độ xem
    @PostMapping("/update-progress")
    public ResponseEntity<?> updateProgress(
            @RequestParam int movieId,
            @RequestParam Double currentTime,
            HttpSession session) { 
        
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        
        if (userSession == null) {
            return ResponseEntity.status(401).build();
        }
        
        watchHistoryService.updateWatchProgress(userSession.getId(), movieId, currentTime);
        return ResponseEntity.ok().build();
    }
}