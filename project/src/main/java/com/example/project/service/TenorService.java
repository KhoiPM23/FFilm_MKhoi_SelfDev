package com.example.project.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Service
public class TenorService {

    @Value("${tenor.api.key}")
    private String tenorApiKey;

    public ResponseEntity<?> getTrendingStickers(int limit, String category) {
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

    public ResponseEntity<?> searchStickers(String q, int limit) {
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
