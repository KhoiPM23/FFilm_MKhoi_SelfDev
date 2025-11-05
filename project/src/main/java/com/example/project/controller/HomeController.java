package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

    @GetMapping("/")
    public String home(Model model) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // Banner - Phim phổ biến nhất
            setBanner(model, restTemplate);
            
            // Các danh mục phim
            setHotMovies(model, restTemplate);
            setNewReleases(model, restTemplate);
            setAnimeHot(model, restTemplate);
            setKidsMovies(model, restTemplate);
            setActionMovies(model, restTemplate);

            return "index";
        } catch (Exception e) {
            System.err.println("ERROR in home(): " + e.getMessage());
            e.printStackTrace();
            
            // Set default values để tránh null pointer
            ensureDefaultAttributes(model);
            return "index";
        }
    }

    private void setBanner(Model model, RestTemplate restTemplate) {
    try {
        // 1) Lấy list popular
        String listUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
        String listResp = restTemplate.getForObject(listUrl, String.class);

        if (listResp != null && !listResp.isEmpty()) {
            JSONObject listJson = new JSONObject(listResp);
            JSONArray results = listJson.optJSONArray("results");

            if (results != null && results.length() > 0) {
                JSONObject firstMovie = results.getJSONObject(0);

                int movieId = firstMovie.optInt("id", -1);
                String title = firstMovie.optString("title", "Unknown");
                String overview = firstMovie.optString("overview", "No description");
                String backdropPath = firstMovie.optString("backdrop_path", "");
                String releaseDate = firstMovie.optString("release_date", "");

                // Default banner map (will override with details if available)
                Map<String, Object> bannerMap = new HashMap<>();
                bannerMap.put("id", movieId);
                bannerMap.put("title", title);
                bannerMap.put("overview", overview);
                bannerMap.put("backdrop", IMAGE_BASE_URL + "/original" + (backdropPath != null ? backdropPath : ""));
                bannerMap.put("rating", firstMovie.optDouble("vote_average", 0.0));
                bannerMap.put("year", (releaseDate != null && releaseDate.length() >= 4) ? releaseDate.substring(0, 4) : "2024");
                bannerMap.put("runtime", 0);
                bannerMap.put("genres", Collections.emptyList());

                // 2) Gọi endpoint chi tiết để lấy runtime + genres (nếu có id hợp lệ)
                if (movieId > 0) {
                    try {
                        String detailUrl = BASE_URL + "/movie/" + movieId + "?api_key=" + API_KEY + "&language=vi-VN";
                        String detailResp = restTemplate.getForObject(detailUrl, String.class);
                        if (detailResp != null && !detailResp.isEmpty()) {
                            JSONObject detailJson = new JSONObject(detailResp);

                            // runtime
                            int runtime = detailJson.optInt("runtime", 0);
                            if (runtime > 0) bannerMap.put("runtime", runtime);
                            else bannerMap.put("runtime", 0); // fallback if not provided

                            // genres
                            JSONArray genres = detailJson.optJSONArray("genres");
                            if (genres != null) {
                                List<String> genreNames = new ArrayList<>();
                                for (int i = 0; i < genres.length(); i++) {
                                    JSONObject g = genres.getJSONObject(i);
                                    genreNames.add(g.optString("name"));
                                }
                                bannerMap.put("genres", genreNames);
                            }

                            // If release_date or backdrop changed in detail, you can override:
                            String detailRelease = detailJson.optString("release_date", "");
                            if (detailRelease != null && detailRelease.length() >= 4) {
                                bannerMap.put("year", detailRelease.substring(0, 4));
                            }
                            String detailBackdrop = detailJson.optString("backdrop_path", "");
                            if (detailBackdrop != null && !detailBackdrop.isEmpty()) {
                                bannerMap.put("backdrop", IMAGE_BASE_URL + "/original" + detailBackdrop);
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: could not fetch movie details for banner: " + ex.getMessage());
                        // don't fail — keep the partial bannerMap
                    }
                }

                model.addAttribute("banner", bannerMap);
                return;
            }
        }
    } catch (Exception e) {
        System.err.println("Error in setBanner: " + e.getMessage());
    }

    // fallback
    model.addAttribute("banner", createDefaultBanner());
}


    private void setHotMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            List<Map<String, String>> movies = getMoviesFromUrl(restTemplate, url, 1, 20);
            model.addAttribute("hotMovies", movies);
        } catch (Exception e) {
            System.err.println("Error in setHotMovies: " + e.getMessage());
            model.addAttribute("hotMovies", new ArrayList<>());
        }
    }

    private void setNewReleases(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/movie/now_playing?api_key=" + API_KEY + "&language=vi-VN&page=1";
            List<Map<String, String>> movies = getMoviesFromUrl(restTemplate, url, 0, 15);
            model.addAttribute("newMovies", movies);
        } catch (Exception e) {
            System.err.println("Error in setNewReleases: " + e.getMessage());
            model.addAttribute("newMovies", new ArrayList<>());
        }
    }

    private void setAnimeHot(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=16&sort_by=popularity.desc&page=1";
            List<Map<String, String>> movies = getMoviesFromUrl(restTemplate, url, 0, 15);
            model.addAttribute("animeMovies", movies);
        } catch (Exception e) {
            System.err.println("Error in setAnimeHot: " + e.getMessage());
            model.addAttribute("animeMovies", new ArrayList<>());
        }
    }

    private void setKidsMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=10751&sort_by=popularity.desc&page=1";
            List<Map<String, String>> movies = getMoviesFromUrl(restTemplate, url, 0, 15);
            model.addAttribute("kidsMovies", movies);
        } catch (Exception e) {
            System.err.println("Error in setKidsMovies: " + e.getMessage());
            model.addAttribute("kidsMovies", new ArrayList<>());
        }
    }

    private void setActionMovies(Model model, RestTemplate restTemplate) {
        try {
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=28&sort_by=popularity.desc&page=1";
            List<Map<String, String>> movies = getMoviesFromUrl(restTemplate, url, 0, 15);
            model.addAttribute("actionMovies", movies);
        } catch (Exception e) {
            System.err.println("Error in setActionMovies: " + e.getMessage());
            model.addAttribute("actionMovies", new ArrayList<>());
        }
    }

    private List<Map<String, String>> getMoviesFromUrl(RestTemplate restTemplate, String url, int startIndex, int maxCount) {
        List<Map<String, String>> movies = new ArrayList<>();
        
        try {
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                return movies;
            }
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");
            
            if (results == null) {
                return movies;
            }

            for (int i = startIndex; i < Math.min(maxCount, results.length()); i++) {
                try {
                    JSONObject movie = results.getJSONObject(i);
                    String posterPath = movie.optString("poster_path", "");
                    
                    if (!posterPath.isEmpty()) {
                        Map<String, String> movieMap = new HashMap<>();
                        movieMap.put("id", String.valueOf(movie.optInt("id")));
                        movieMap.put("title", movie.optString("title", "Unknown"));
                        movieMap.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movieMap.put("rating", String.format("%.1f", movie.optDouble("vote_average", 0.0)));
                        movieMap.put("overview", movie.optString("overview", ""));
                    movieMap.put("releaseDate", movie.optString("release_date", ""));
                        movies.add(movieMap);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing movie at index " + i);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching from: " + url + " - " + e.getMessage());
        }
        
        return movies;
    }
    
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("title", "Welcome to FFilm");
        banner.put("overview", "Discover amazing movies and TV shows");
        banner.put("backdrop", "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg");
        banner.put("rating", 8.5);
        banner.put("year", "2024");
        return banner;
    }
    
    private void ensureDefaultAttributes(Model model) {
        if (!model.containsAttribute("banner")) {
            model.addAttribute("banner", createDefaultBanner());
        }
        if (!model.containsAttribute("hotMovies")) {
            model.addAttribute("hotMovies", new ArrayList<>());
        }
        if (!model.containsAttribute("newMovies")) {
            model.addAttribute("newMovies", new ArrayList<>());
        }
        if (!model.containsAttribute("animeMovies")) {
            model.addAttribute("animeMovies", new ArrayList<>());
        }
        if (!model.containsAttribute("kidsMovies")) {
            model.addAttribute("kidsMovies", new ArrayList<>());
        }
        if (!model.containsAttribute("actionMovies")) {
            model.addAttribute("actionMovies", new ArrayList<>());
        }
    }
}