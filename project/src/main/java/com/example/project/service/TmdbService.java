package com.example.project.service;

import com.example.project.dto.TmdbMovieDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TmdbService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    private final String TMDB_BASE_URL = "https.api.themoviedb.org/3";

    /**
     * Gọi TMDB API để lấy chi tiết phim bằng TMDB ID
     */
    public TmdbMovieDto fetchMovieById(int tmdbId) {
        String url = String.format(
            "%s/movie/%d?api_key=%s&language=vi-VN",
            TMDB_BASE_URL, tmdbId, tmdbApiKey
        );
        
        try {
            // Dùng DTO để hứng kết quả
            return restTemplate.getForObject(url, TmdbMovieDto.class);
        } catch (Exception e) {
            System.err.println("Lỗi khi gọi TMDB API: " + e.getMessage());
            throw new RuntimeException("Không thể lấy dữ liệu từ TMDB: " + e.getMessage(), e);
        }
    }
}