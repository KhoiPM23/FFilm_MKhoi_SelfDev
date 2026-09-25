package com.example.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Shared TMDB API transport layer.
 * Owns: API key, base URL, and HTTP invocation via RestTemplate.
 * Does NOT own: Domain logic, JSON parsing, or orchestration.
 */
@Component
public class TmdbClient {

    private final RestTemplate restTemplate;

    @Value("${tmdb.api.key}")
    private String apiKey;

    private static final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired
    public TmdbClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Executes a GET request against the TMDB API.
     * @param path The endpoint path, e.g., "/movie/123" or "/discover/movie"
     * @param queryParams Additional query parameters (without leading "&" or "?"), e.g., "language=vi-VN&page=1". Can be null or empty.
     * @return The raw JSON response as a String.
     */
    public String get(String path, String queryParams) {
        String url = BASE_URL + path + "?api_key=" + apiKey;
        if (queryParams != null && !queryParams.isEmpty()) {
            if (!queryParams.startsWith("&")) {
                url += "&";
            }
            url += queryParams;
        }
        return restTemplate.getForObject(url, String.class);
    }
}
