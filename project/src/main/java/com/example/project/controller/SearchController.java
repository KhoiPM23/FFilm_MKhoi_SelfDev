package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person; // [G42] Thêm import
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class SearchController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    
    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;

    // [Dán toàn bộ code này vào file SearchController.java]

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
        
        if (query == null || query.trim().isEmpty()) {
            setEmptyResults(model, null);
            return "search";
        }
        
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

            // [SỬA] BƯỚC 1: LẤY KẾT QUẢ CỦA PAGE HIỆN TẠI (ĐỂ HIỂN THỊ GRID)
            String searchUrl = BASE_URL + "/search/multi?api_key=" + API_KEY + 
                               "&language=vi-VN&query=" + encodedQuery + 
                               "&page=" + page + "&include_adult=false";
                               
            String response = restTemplate.getForObject(searchUrl, String.class);
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");

            List<Map<String, Object>> searchResults = new ArrayList<>(); 
            List<Integer> currentPageResultIds = new ArrayList<>(); // ID của trang này
            Integer topResultIdPage1 = null; // ID top 1 của trang 1

            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String mediaType = item.optString("media_type", "movie");
                    
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        
                        Movie movie = movieService.syncMovieFromList(item); 
                        
                        if (movie != null) {
                            searchResults.add(movieService.convertToMap(movie));
                            currentPageResultIds.add(movie.getTmdbId()); 
                        }
                    } 
                    else if (mediaType.equals("person")) {
                        
                        Person person = movieService.getPersonPartialOrSync(item);

                        if (person != null) {
                            Map<String, Object> personAsMovie = new HashMap<>();
                            personAsMovie.put("id", person.getTmdbId()); 
                            personAsMovie.put("tmdbId", person.getTmdbId());
                            personAsMovie.put("title", person.getFullName());
                            personAsMovie.put("rating", String.format("%.1f", person.getPopularity() != null ? person.getPopularity() / 10 : 0.0));
                            personAsMovie.put("poster", person.getProfilePath() != null 
                                ? "https://image.tmdb.org/t/p/w500" + person.getProfilePath() 
                                : "/images/placeholder-person.jpg");
                            personAsMovie.put("year", "Diễn viên"); 
                            personAsMovie.put("isPerson", true); 
                            searchResults.add(personAsMovie);
                        }
                    }
                }
            }

            // [SỬA] BƯỚC 2: LẤY DỮ LIỆU GỐC CHO CÁC CAROUSEL (LUÔN TỪ PAGE 1)
            // Chỉ gọi API page 1 nếu ta chưa có (ví dụ khi đang ở page 2+)
            if (page > 1 && !searchResults.isEmpty()) {
                try {
                    String page1SearchUrl = BASE_URL + "/search/multi?api_key=" + API_KEY + 
                                          "&language=vi-VN&query=" + encodedQuery + 
                                          "&page=1&include_adult=false";
                    String page1Response = restTemplate.getForObject(page1SearchUrl, String.class);
                    JSONArray page1Results = new JSONObject(page1Response).optJSONArray("results");
                    if (page1Results != null && page1Results.length() > 0) {
                        // Cẩn thận tìm phim đầu tiên, vì multi có thể có person
                        for (int i = 0; i < page1Results.length(); i++) {
                            JSONObject item = page1Results.getJSONObject(i);
                            if (item.optString("media_type", "movie").equals("movie") || item.optString("media_type", "movie").equals("tv")) {
                                topResultIdPage1 = item.optInt("id");
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi lấy topResultId (page 1): " + e.getMessage());
                }
            } else if (!currentPageResultIds.isEmpty()) {
                // Tiết kiệm API: Nếu đang ở page 1, lấy luôn ID top
                topResultIdPage1 = currentPageResultIds.get(0);
            }

            // [SỬA] BƯỚC 3: GỌI HÀM MỚI ĐỂ FILL ĐẦY CAROUSEL
            // Các hàm này sẽ tự lọc ID trùng với 'currentPageResultIds'
            List<Map<String, Object>> aiSuggestions = loadAiSuggestions(topResultIdPage1, currentPageResultIds);
            List<Map<String, Object>> relatedMovies = loadRelatedMovies(encodedQuery, topResultIdPage1, currentPageResultIds);
            
            model.addAttribute("aiSuggestions", aiSuggestions); 
            model.addAttribute("relatedMovies", relatedMovies); // [THÊM]
            
            model.addAttribute("searchResults", searchResults); 
            model.addAttribute("query", query);
            model.addAttribute("totalResults", json.optInt("total_results", 0));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", Math.min(json.optInt("total_pages", 1), 500));
            model.addAttribute("hasResults", !searchResults.isEmpty());
            
            // (Giữ nguyên các model.addAttribute filter)
            model.addAttribute("selectedGenres", genres != null ? genres : "");
            model.addAttribute("yearFrom", yearFrom != null ? yearFrom : "");
            model.addAttribute("yearTo", yearTo != null ? yearTo : "");
            model.addAttribute("minRating", minRating != null ? minRating : "0");
            model.addAttribute("quickFilter", quickFilter != null ? quickFilter : "");
            
            return "search";
            
        } catch (Exception e) {
            e.printStackTrace();
            setEmptyResults(model, query);
            return "search";
        }
    }
    
    // [THÊM] HÀM MỚI: LOAD PHIM LIÊN QUAN (3 BẬC ƯU TIÊN)
    private List<Map<String, Object>> loadRelatedMovies(String encodedQuery, Integer topResultId, List<Integer> excludeIds) {
        // java.util.Set và java.util.HashSet
        Set<Integer> addedIds = new HashSet<>(excludeIds);
        List<Map<String, Object>> movies = new ArrayList<>();
        int limit = 20;

        try {
            // Bậc 1: Dùng API Search
            String searchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
            addMoviesToList(searchUrl, movies, addedIds, limit);
            if (movies.size() >= limit) return movies;

            // Bậc 2: Dùng API Similar (Nếu có topResultId)
            if (topResultId != null) {
                String similarUrl = BASE_URL + "/movie/" + topResultId + "/similar?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(similarUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }

            // Bậc 3: Dùng API Popular (Để fill đầy)
            String popularUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            addMoviesToList(popularUrl, movies, addedIds, limit);
            
        } catch (Exception e) {
            System.err.println("Lỗi loadRelatedMovies: " + e.getMessage());
        }
        return movies;
    }

    // [THÊM] HÀM MỚI: LOAD GỢI Ý AI (3 BẬC ƯU TIÊN)
    private List<Map<String, Object>> loadAiSuggestions(Integer topResultId, List<Integer> excludeIds) {
        Set<Integer> addedIds = new HashSet<>(excludeIds);
        List<Map<String, Object>> movies = new ArrayList<>();
        int limit = 20;

        try {
            // Bậc 1: Dùng API Recommendations (Nếu có topResultId)
            if (topResultId != null) {
                String recommendUrl = BASE_URL + "/movie/" + topResultId + "/recommendations?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(recommendUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }

            // Bậc 2: Dùng API Similar (Fallback nếu Bậc 1 không đủ)
            if (topResultId != null) {
                String similarUrl = BASE_URL + "/movie/" + topResultId + "/similar?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(similarUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }
            
            // Bậc 3: Dùng API Trending (Để fill đầy)
            String trendingUrl = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN&page=1";
            addMoviesToList(trendingUrl, movies, addedIds, limit);

        } catch (Exception e) {
            System.err.println("Lỗi loadAiSuggestions: " + e.getMessage());
        }
        return movies;
    }

    // [THÊM] HÀM HELPER: Gọi API và thêm phim vào danh sách
    private void addMoviesToList(String apiUrl, List<Map<String, Object>> movies, Set<Integer> addedIds, int limit) {
        try {
            Map<String, Object> data = movieService.loadAndSyncPaginatedMovies(apiUrl, limit);
            List<Map<String, Object>> fetchedMovies = (List<Map<String, Object>>) data.get("movies");
            
            for (Map<String, Object> movie : fetchedMovies) {
                if (movies.size() >= limit) break;
                int tmdbId = (int) movie.get("id");
                if (!addedIds.contains(tmdbId)) {
                    movies.add(movie);
                    addedIds.add(tmdbId);
                }
            }
        } catch (Exception e) {
             System.err.println("Lỗi addMoviesToList: " + e.getMessage());
        }
    }
    
    // [SỬA] HÀM CŨ: Thêm 'relatedMovies' vào
    private void setEmptyResults(Model model, String query) {
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("aiSuggestions", new ArrayList<>());
        model.addAttribute("relatedMovies", new ArrayList<>()); // [THÊM]
        model.addAttribute("query", query);
        model.addAttribute("totalResults", 0);
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 1);
        model.addAttribute("hasResults", false);
        model.addAttribute("selectedGenres", "");
        model.addAttribute("yearFrom", "");
        model.addAttribute("yearTo", "");
        model.addAttribute("minRating", "0");
        model.addAttribute("quickFilter", "");
    }
}