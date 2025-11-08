package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class SearchController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String yearFrom,
            @RequestParam(required = false) String yearTo,
            @RequestParam(required = false) String minRating,
            @RequestParam(required = false) String quickFilter,
            Model model) {
        
        // ========== CASE 1: Không có query -> Trang search trống với trending ==========
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("hasResults", false);
            model.addAttribute("searchResults", new ArrayList<>());
            model.addAttribute("relatedMovies", new ArrayList<>());
            model.addAttribute("aiSuggestions", new ArrayList<>());
            setDefaultAttributes(model);
            return "search";
        }
        
        // ========== CASE 2: Có query -> Thực hiện tìm kiếm ==========
        try {
            RestTemplate restTemplate = new RestTemplate();
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            
            // Build search URL
            String searchUrl = buildSearchUrl(encodedQuery, page, genres, yearFrom, yearTo, minRating, quickFilter);
            String response = restTemplate.getForObject(searchUrl, String.class);
            
            if (response == null || response.isEmpty()) {
                setEmptyResults(model, query);
                return "search";
            }
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");
            int totalResults = json.optInt("total_results", 0);
            int totalPages = json.optInt("total_pages", 1);
            
            List<Map<String, Object>> movies = new ArrayList<>();
            
            if (results != null && results.length() > 0) {
                for (int i = 0; i < results.length(); i++) {
                    try {
                        JSONObject item = results.getJSONObject(i);
                        String posterPath = item.optString("poster_path", "");
                        
                        Map<String, Object> movie = new HashMap<>();
                        movie.put("id", item.optInt("id"));
                        movie.put("title", item.optString("title", item.optString("name", "Unknown")));
                        movie.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movie.put("rating", String.format("%.1f", item.optDouble("vote_average", 0.0)));
                        movie.put("year", extractYear(item));
                        movie.put("overview", item.optString("overview", ""));
                        
                        JSONArray genreIds = item.optJSONArray("genre_ids");
                        List<Integer> genresList = new ArrayList<>();
                        if (genreIds != null) {
                            for (int j = 0; j < genreIds.length(); j++) {
                                genresList.add(genreIds.getInt(j));
                            }
                        }
                        movie.put("genreIds", genresList);
                        
                        movies.add(movie);
                    } catch (Exception e) {
                        System.err.println("Error parsing movie: " + e.getMessage());
                    }
                }
            }
            
            // Get AI suggestions (related movies based on query)
            List<Map<String, Object>> aiSuggestions = getAISuggestions(restTemplate, query, movies);
            
            boolean hasResults = !movies.isEmpty();
            
            // Add to model
            model.addAttribute("searchResults", movies);
            model.addAttribute("aiSuggestions", aiSuggestions);
            model.addAttribute("relatedMovies", new ArrayList<>()); // Không cần nữa
            model.addAttribute("query", query);
            model.addAttribute("totalResults", totalResults);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasResults", hasResults);
            
            // Preserve filters
            model.addAttribute("selectedGenres", genres != null ? genres : "");
            model.addAttribute("yearFrom", yearFrom != null ? yearFrom : "");
            model.addAttribute("yearTo", yearTo != null ? yearTo : "");
            model.addAttribute("minRating", minRating != null ? minRating : "0");
            model.addAttribute("quickFilter", quickFilter != null ? quickFilter : "");
            
            return "search";
            
        } catch (Exception e) {
            System.err.println("Search error: " + e.getMessage());
            e.printStackTrace();
            setEmptyResults(model, query);
            model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "search";
        }
    }
    
    private String buildSearchUrl(String query, int page, String genres, String yearFrom, 
                               String yearTo, String minRating, String quickFilter) {
        StringBuilder url = new StringBuilder();
        
        // Nếu có filters -> dùng discover + search
        if (hasFilters(genres, yearFrom, yearTo, minRating, quickFilter)) {
            // Bước 1: Search để lấy IDs
            // Bước 2: Discover với filters
            // => Phức tạp, NÊN đơn giản hóa: chỉ dùng search, filter ở client
            
            url.append(BASE_URL).append("/search/multi");
            url.append("?api_key=").append(API_KEY);
            url.append("&language=vi-VN");
            url.append("&query=").append(query);
            url.append("&page=").append(page);
            url.append("&include_adult=false");
        } else {
            // Simple search
            url.append(BASE_URL).append("/search/multi");
            url.append("?api_key=").append(API_KEY);
            url.append("&language=vi-VN");
            url.append("&query=").append(query);
            url.append("&page=").append(page);
            url.append("&include_adult=false");
        }
        
        return url.toString();
    }
    
    private boolean hasFilters(String genres, String yearFrom, String yearTo, 
                               String minRating, String quickFilter) {
        return (genres != null && !genres.isEmpty()) ||
               (yearFrom != null && !yearFrom.isEmpty()) ||
               (yearTo != null && !yearTo.isEmpty()) ||
               (minRating != null && !minRating.equals("0")) ||
               (quickFilter != null && !quickFilter.isEmpty());
    }
    
    private String extractYear(JSONObject item) {
        String releaseDate = item.optString("release_date", 
                            item.optString("first_air_date", ""));
        if (releaseDate != null && releaseDate.length() >= 4) {
            return releaseDate.substring(0, 4);
        }
        return "";
    }
    
    private List<Map<String, Object>> getRelatedMovies(RestTemplate restTemplate, 
                                                        Map<String, Object> firstMovie) {
        List<Map<String, Object>> related = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Integer> genreIds = (List<Integer>) firstMovie.get("genreIds");
            
            if (genreIds == null || genreIds.isEmpty()) {
                return related;
            }
            
            String genreId = String.valueOf(genreIds.get(0));
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN&with_genres=" + genreId + 
                        "&sort_by=popularity.desc&page=1";
            
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return related;
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");
            
            if (results != null) {
                for (int i = 0; i < Math.min(8, results.length()); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String posterPath = item.optString("poster_path", "");
                    
                    if (!posterPath.isEmpty()) {
                        Map<String, Object> movie = new HashMap<>();
                        movie.put("id", item.optInt("id"));
                        movie.put("title", item.optString("title", "Unknown"));
                        movie.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movie.put("rating", String.format("%.1f", item.optDouble("vote_average", 0.0)));
                        related.add(movie);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching related movies: " + e.getMessage());
        }
        
        return related;
    }

    private List<Map<String, Object>> getAISuggestions(RestTemplate restTemplate, String query, List<Map<String, Object>> searchResults) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        try {
            // Lấy genre từ kết quả đầu tiên (nếu có)
            String genreId = "";
            if (!searchResults.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Integer> genreIds = (List<Integer>) searchResults.get(0).get("genreIds");
                if (genreIds != null && !genreIds.isEmpty()) {
                    genreId = String.valueOf(genreIds.get(0));
                }
            }
            
            // Fetch similar movies
            String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + 
                        "&language=vi-VN" +
                        (genreId.isEmpty() ? "" : "&with_genres=" + genreId) +
                        "&sort_by=popularity.desc&vote_count.gte=50&page=1";
            
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return suggestions;
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");
            
            if (results != null) {
                // Lấy 20 phim đầu tiên
                for (int i = 0; i < Math.min(20, results.length()); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String posterPath = item.optString("poster_path", "");
                    
                    if (!posterPath.isEmpty()) {
                        Map<String, Object> movie = new HashMap<>();
                        movie.put("id", item.optInt("id"));
                        movie.put("title", item.optString("title", "Unknown"));
                        movie.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movie.put("rating", String.format("%.1f", item.optDouble("vote_average", 0.0)));
                        movie.put("year", extractYear(item));
                        suggestions.add(movie);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching AI suggestions: " + e.getMessage());
        }
        
        return suggestions;
    }
    
    private void setEmptyResults(Model model, String query) {
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("relatedMovies", new ArrayList<>());
        model.addAttribute("aiSuggestions", new ArrayList<>());
        model.addAttribute("query", query);
        model.addAttribute("totalResults", 0);
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 1);
        model.addAttribute("hasResults", false);
        setDefaultAttributes(model);
    }
    
    private void setDefaultAttributes(Model model) {
        if (!model.containsAttribute("selectedGenres")) {
            model.addAttribute("selectedGenres", "");
        }
        if (!model.containsAttribute("yearFrom")) {
            model.addAttribute("yearFrom", "");
        }
        if (!model.containsAttribute("yearTo")) {
            model.addAttribute("yearTo", "");
        }
        if (!model.containsAttribute("minRating")) {
            model.addAttribute("minRating", "0");
        }
        if (!model.containsAttribute("quickFilter")) {
            model.addAttribute("quickFilter", "");
        }
    }
}