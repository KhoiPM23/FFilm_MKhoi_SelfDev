package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.project.service.TenorService;
@RestController
@RequestMapping("/api/tenor")
public class TenorController {
    
    @Autowired
    private TenorService tenorService;
    
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingStickers(
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(required = false) String category) {
        
        return tenorService.getTrendingStickers(limit, category);
    }
    
    @GetMapping("/search")
    public ResponseEntity<?> searchStickers(
            @RequestParam String q,
            @RequestParam(defaultValue = "24") int limit) {
        
        return tenorService.searchStickers(q, limit);
    }
}
