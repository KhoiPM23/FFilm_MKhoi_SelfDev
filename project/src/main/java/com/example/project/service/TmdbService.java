package com.example.project.service;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

public class TmdbService {
    @Service
    @RequiredArgsConstructor
    @CacheConfig(cacheNames = "movies")
    public class TmdbService {

        private final RestClient restClient;
        private final TmdbConfig config;

        private static final String LANG = "vi-VN";

        @Cacheable(key = "#endpoint + #page")
        public TmdbPageResponse getMovies(String endpoint, int page, int limit) {
            String url = UriComponentsBuilder.fromHttpUrl(config.getBaseUrl() + endpoint)
                    .queryParam("api_key", config.getApiKey())
                    .queryParam("language", LANG)
                    .queryParam("page", page)
                    .build()
                    .toUriString();

            TmdbPageResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(TmdbPageResponse.class);

            if (response == null || response.results() == null) {
                return new TmdbPageResponse(List.of(), 0, 0);
            }

            List<MovieSummary> movies = response.results().stream()
                    .filter(m -> m.poster_path() != null)
                    .limit(limit)
                    .map(this::toSummary)
                    .toList();

            return new TmdbPageResponse(movies, response.total_pages(), page);
        }

        private MovieSummary toSummary(TmdbMovie movie) {
            String poster = movie.poster_path() != null
                    ? config.getImageUrl() + "/w500" + movie.poster_path()
                    : "/images/placeholder.jpg";

            return new MovieSummary(
                    movie.id(),
                    movie.title(),
                    poster,
                    String.format("%.1f", movie.vote_average()),
                    movie.overview(),
                    movie.release_date());
        }

        // Các method tiện ích
        public TmdbPageResponse getPopular(int page, int limit) {
            return getMovies("/movie/popular", page, limit);
        }

        public TmdbPageResponse getNowPlaying(int page, int limit) {
            return getMovies("/movie/now_playing", page, limit);
        }

        public TmdbPageResponse getByGenre(int genreId, int page, int limit) {
            String endpoint = "/discover/movie?with_genres=" + genreId + "&sort_by=popularity.desc";
            return getMovies(endpoint, page, limit);
        }
    }
}
