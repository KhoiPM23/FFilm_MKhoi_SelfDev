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
            @RequestParam(defaultValue = "false") boolean aiSearch,
            Model model) {
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Validate query
            if (query == null || query.trim().isEmpty()) {
                model.addAttribute("error", "Vui lòng nhập từ khóa tìm kiếm");
                model.addAttribute("searchResults", new ArrayList<>());
                model.addAttribute("relatedMovies", new ArrayList<>());
                setDefaultAttributes(model);
                return "search";
            }

            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            
            // Build search URL with filters
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
                        
                        // Only include movies/TV with posters
                        String posterPath = item.optString("poster_path", "");
                        if (posterPath.isEmpty()) continue;
                        
                        Map<String, Object> movie = new HashMap<>();
                        movie.put("id", item.optInt("id"));
                        movie.put("title", item.optString("title", item.optString("name", "Unknown")));
                        movie.put("poster", IMAGE_BASE_URL + "/w500" + posterPath);
                        movie.put("rating", String.format("%.1f", item.optDouble("vote_average", 0.0)));
                        movie.put("year", extractYear(item));
                        movie.put("overview", item.optString("overview", ""));
                        movie.put("mediaType", item.optString("media_type", "movie"));
                        
                        // Genre IDs for filtering
                        JSONArray genreIds = item.optJSONArray("genre_ids");
                        List<Integer> genres_list = new ArrayList<>();
                        if (genreIds != null) {
                            for (int j = 0; j < genreIds.length(); j++) {
                                genres_list.add(genreIds.getInt(j));
                            }
                        }
                        movie.put("genreIds", genres_list);
                        
                        movies.add(movie);
                    } catch (Exception e) {
                        System.err.println("Error parsing movie item: " + e.getMessage());
                    }
                }
            }
            
            // Get related movies based on first result's genre
            List<Map<String, Object>> relatedMovies = new ArrayList<>();
            if (!movies.isEmpty()) {
                relatedMovies = getRelatedMovies(restTemplate, movies.get(0));
            }
            
            // Add attributes to model
            model.addAttribute("searchResults", movies);
            model.addAttribute("relatedMovies", relatedMovies);
            model.addAttribute("query", query);
            model.addAttribute("totalResults", totalResults);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasResults", !movies.isEmpty());
            
            // Preserve filters for UI sync
            model.addAttribute("selectedGenres", genres != null ? genres : "");
            model.addAttribute("yearFrom", yearFrom != null ? yearFrom : "");
            model.addAttribute("yearTo", yearTo != null ? yearTo : "");
            model.addAttribute("minRating", minRating != null ? minRating : "0");
            model.addAttribute("quickFilter", quickFilter != null ? quickFilter : "");
            
            return "search";
            
        } catch (Exception e) {
            System.err.println("ERROR in search(): " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Có lỗi xảy ra khi tìm kiếm");
            setEmptyResults(model, query);
            return "search";
        }
    }
    
    private String buildSearchUrl(String query, int page, String genres, String yearFrom, 
                                   String yearTo, String minRating, String quickFilter) {
        StringBuilder url = new StringBuilder();
        
        // Use discover if filters are applied, otherwise use search
        if (hasFilters(genres, yearFrom, yearTo, minRating, quickFilter)) {
            url.append(BASE_URL).append("/discover/movie");
            url.append("?api_key=").append(API_KEY);
            url.append("&language=vi-VN");
            url.append("&page=").append(page);
            url.append("&query=").append(query); // Still include query for relevance
            
            // Apply filters
            if (genres != null && !genres.isEmpty()) {
                url.append("&with_genres=").append(genres);
            }
            if (yearFrom != null && !yearFrom.isEmpty()) {
                url.append("&primary_release_date.gte=").append(yearFrom).append("-01-01");
            }
            if (yearTo != null && !yearTo.isEmpty()) {
                url.append("&primary_release_date.lte=").append(yearTo).append("-12-31");
            }
            if (minRating != null && !minRating.isEmpty() && !minRating.equals("0")) {
                url.append("&vote_average.gte=").append(minRating);
            }
            
            // Quick filters
            if ("trending".equals(quickFilter)) {
                url.append("&sort_by=popularity.desc");
            } else if ("new".equals(quickFilter)) {
                url.append("&sort_by=release_date.desc");
            } else if ("top-rated".equals(quickFilter)) {
                url.append("&sort_by=vote_average.desc&vote_count.gte=100");
            } else {
                url.append("&sort_by=popularity.desc");
            }
        } else {
            // Simple search without filters
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
    
    private void setEmptyResults(Model model, String query) {
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("relatedMovies", new ArrayList<>());
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