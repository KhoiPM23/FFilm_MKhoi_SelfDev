package com.example.project.service;

import com.example.project.dto.TmdbMovieDto;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class TmdbService {
    @Autowired private RestTemplate restTemplate;
    @Value("${tmdb.api.key}") private String tmdbApiKey;
    private final String TMDB_BASE_URL = "https://api.themoviedb.org/3";

    @CircuitBreaker(name = "tmdbService", fallbackMethod = "fallbackFetch")
    @RateLimiter(name = "tmdbService")
    public TmdbMovieDto fetchMovieById(int tmdbId) {
        String url = String.format("%s/movie/%d?api_key=%s&language=vi-VN", TMDB_BASE_URL, tmdbId, tmdbApiKey);
        return restTemplate.getForObject(url, TmdbMovieDto.class);
    }

    public List<Integer> fetchPopularMovieIds(int page) {
        String url = String.format("%s/movie/popular?api_key=%s&language=vi-VN&page=%d", TMDB_BASE_URL, tmdbApiKey, page);
        try {
            Map response = restTemplate.getForObject(url, Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            List<Integer> ids = new ArrayList<>();
            if (results != null) {
                for (Map<String, Object> m : results) ids.add((Integer) m.get("id"));
            }
            return ids;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public TmdbMovieDto fallbackFetch(int id, Throwable t) { return null; }
}