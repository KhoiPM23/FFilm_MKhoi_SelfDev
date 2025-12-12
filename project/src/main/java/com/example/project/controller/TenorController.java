package com.example.project.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/tenor")
public class TenorController {
    
    @Value("${tenor.api.key}")
    private String tenorApiKey;
    
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingStickers(
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(required = false) String category) {
        
        String url = "https://tenor.googleapis.com/v2/featured" +
                    "?key=" + tenorApiKey +
                    "&limit=" + limit +
                    "&media_filter=gif,tinygif" +
                    "&client_key=FFilm_Connect";
        
        if (category != null && !category.equals("trending")) {
            url += "&q=" + URLEncoder.encode(category, StandardCharsets.UTF_8);
        }
        
        return proxyToTenor(url);
    }
    
    @GetMapping("/search")
    public ResponseEntity<?> searchStickers(
            @RequestParam String q,
            @RequestParam(defaultValue = "24") int limit) {
        
        String url = "https://tenor.googleapis.com/v2/search" +
                    "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) +
                    "&key=" + tenorApiKey +
                    "&limit=" + limit +
                    "&media_filter=gif,tinygif" +
                    "&client_key=FFilm_Connect";
        
        return proxyToTenor(url);
    }
    
    private ResponseEntity<?> proxyToTenor(String url) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) throws IOException {
                    // Don't throw exception on 4xx/5xx
                    return false;
                }
            });
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(response.getBody());
            } else {
                // Return empty results instead of error
                return ResponseEntity.ok("{\"results\":[],\"next\":\"0\"}");
            }
        } catch (Exception e) {
            // Return empty results
            return ResponseEntity.ok("{\"results\":[],\"next\":\"0\",\"error\":\"Service unavailable\"}");
        }
    }
}

