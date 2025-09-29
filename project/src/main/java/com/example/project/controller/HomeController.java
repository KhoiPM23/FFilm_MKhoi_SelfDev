package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
            String url = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            String response = restTemplate.getForObject(url, String.class);
            
            if (response != null && !response.isEmpty()) {
                JSONObject json = new JSONObject(response);
                JSONArray results = json.getJSONArray("results");

                if (results.length() > 0) {
                    JSONObject firstMovie = results.getJSONObject(0);
                    
                    Map<String, Object> bannerMap = new HashMap<>();
                    bannerMap.put("title", firstMovie.optString("title", "Unknown"));
                    bannerMap.put("overview", firstMovie.optString("overview", "No description"));
                    bannerMap.put("backdrop", IMAGE_BASE_URL + "/original" + firstMovie.optString("backdrop_path", ""));
                    bannerMap.put("rating", firstMovie.optDouble("vote_average", 0.0));
                    
                    String releaseDate = firstMovie.optString("release_date", "2024-01-01");
                    bannerMap.put("year", releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "2024");
                    
                    model.addAttribute("banner", bannerMap);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Error in setBanner: " + e.getMessage());
        }
        
        // Fallback
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
                        movieMap.put("title", movie.optString("title", "Unknown"));
                        movieMap.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movieMap.put("rating", String.format("%.1f", movie.optDouble("vote_average", 0.0)));
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