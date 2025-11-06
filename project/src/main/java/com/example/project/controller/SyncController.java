// SyncController.java
package com.example.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.service.MovieSyncService;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final MovieSyncService syncService;

    public SyncController(MovieSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/movie/{tmdbId}")
    public ResponseEntity<String> syncMovie(@PathVariable int tmdbId) {
        try {
            syncService.syncMovieFromTmdb(tmdbId);
            return ResponseEntity.ok("Đã sync phim TMDB ID: " + tmdbId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/tv/{tmdbId}")
    public ResponseEntity<String> syncTv(@PathVariable int tmdbId) {
        try {
            syncService.syncTvShowFromTmdb(tmdbId);
            return ResponseEntity.ok("Đã sync series TMDB ID: " + tmdbId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // Sync nhanh 10 phim nổi tiếng
    @GetMapping("/demo")
    public ResponseEntity<String> syncDemo() {
        int[] movieIds = { 550, 27205, 155, 13, 680, 429, 122, 240, 424, 98 }; // Fight Club, Inception...
        int count = 0;
        for (int id : movieIds) {
            try {
                syncService.syncMovieFromTmdb(id);
                count++;
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok("Đã sync " + count + " phim demo!");
    }
}