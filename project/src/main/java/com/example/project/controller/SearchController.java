package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors; // <-- THÊM IMPORT NÀY

@Controller
public class SearchController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    
    // === THÊM HẰNG SỐ PAGE_SIZE ===
    private static final int PAGE_SIZE = 20;

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String yearFrom,
            @RequestParam(required = false) String yearTo,
            @RequestParam(required = false) String minRating,
            @RequestParam(required = false) String quickFilter,
            HttpServletRequest request, 
            Model model) {
        
        if (query == null || query.trim().isEmpty()) {
            setEmptyResults(model, null);
            return "search";
        }
        
        JSONObject paginationJson = null; 

        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            
            List<Map<String, Object>> finalSearchResults = new ArrayList<>();
            Set<Integer> addedTmdbIds = new HashSet<>();
            Integer topResultIdPage1 = null;
            
            int dbResultsCount = 0; // <-- Thêm biến đếm DB

            // [ƯU TIÊN 1] BƯỚC 1: Tìm trong Database (DB) của bạn TRƯỚC.
            if (page == 1) {
                List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
                dbResultsCount = dbResults.size(); // <-- Đếm kết quả DB
                
                for (Movie movie : dbResults) {
                    // === SỬA LỖI (BUG 1) ===
                    // Không gọi getMoviePartial nữa. 
                    // Phim đã ở trong DB, chỉ cần convert nó.
                    finalSearchResults.add(movieService.convertToMap(movie));
                    // =======================
                    if (movie.getTmdbId() != null) {
                        addedTmdbIds.add(movie.getTmdbId());
                    }
                }
            }

            // [ƯU TIÊN 2] BƯỚC 2: Tìm trên TMDB API (Theo Title Phim)
            String movieSearchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + 
                               "&language=vi-VN&query=" + encodedQuery + 
                               "&page=" + page + "&include_adult=false";
                               
            String movieResponse = restTemplate.getForObject(movieSearchUrl, String.class);
            paginationJson = new JSONObject(movieResponse); // <-- Lưu JSON này lại
            JSONArray movieResults = paginationJson.optJSONArray("results");

            if (movieResults != null) {
                // === FIX BUG 2: Giới hạn số lượng thêm vào ===
                int addedFromApi = 0; 
                for (int i = 0; i < movieResults.length(); i++) {
                    // Dừng lại nếu trang 1 đã đủ 20 phim (tính cả phim từ DB)
                    if (page == 1 && finalSearchResults.size() >= PAGE_SIZE) {
                        break;
                    }
                    
                    JSONObject item = movieResults.getJSONObject(i);
                    int tmdbId = item.optInt("id", -1);
                    
                    if (tmdbId > 0 && !addedTmdbIds.contains(tmdbId)) {
                        Movie movie = movieService.syncMovieFromList(item); 
                        
                        if (movie != null) {
                            finalSearchResults.add(movieService.convertToMap(movie));
                            addedTmdbIds.add(movie.getTmdbId()); 
                            addedFromApi++;
                        }
                    }
                }
            }
            
            // [ƯU TIÊN 3] BƯỚC 2.5: TÌM PHIM TỪ DIỄN VIÊN/ĐẠO DIỄN (Chỉ chạy ở trang 1)
            if (page == 1 && finalSearchResults.size() < PAGE_SIZE) { // <-- Chỉ chạy nếu còn chỗ
                try {
                    String personSearchUrl = BASE_URL + "/search/person?api_key=" + API_KEY + 
                                             "&language=vi-VN&query=" + encodedQuery + "&page=1";
                    String personResponse = restTemplate.getForObject(personSearchUrl, String.class);
                    JSONArray personResults = new JSONObject(personResponse).optJSONArray("results");

                    if (personResults != null) {
                        for (int i = 0; i < Math.min(personResults.length(), 2); i++) {
                            // Dừng nếu đã đủ 20 phim
                            if (finalSearchResults.size() >= PAGE_SIZE) break;

                            JSONObject person = personResults.getJSONObject(i);
                            int personId = person.optInt("id");
                            if (personId <= 0) continue;

                            String creditsUrl = BASE_URL + "/person/" + personId + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
                            String creditsResponse = restTemplate.getForObject(creditsUrl, String.class);
                            JSONObject creditsJson = new JSONObject(creditsResponse);
                            
                            JSONArray castMovies = creditsJson.optJSONArray("cast");
                            JSONArray crewMovies = creditsJson.optJSONArray("crew");
                            JSONArray allCredits = new JSONArray();
                            if (castMovies != null) castMovies.forEach(allCredits::put);
                            if (crewMovies != null) crewMovies.forEach(allCredits::put);

                            for (int j = 0; j < allCredits.length(); j++) {
                                // Dừng nếu đã đủ 20 phim
                                if (finalSearchResults.size() >= PAGE_SIZE) break;

                                JSONObject movieCredit = allCredits.getJSONObject(j);
                                int movieTmdbId = movieCredit.optInt("id");
                                
                                if (movieTmdbId > 0 && !addedTmdbIds.contains(movieTmdbId)) {
                                    Movie movie = movieService.syncMovieFromList(movieCredit); 
                                    if (movie != null) {
                                        finalSearchResults.add(movieService.convertToMap(movie));
                                        addedTmdbIds.add(movie.getTmdbId());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi tìm kiếm phim theo diễn viên: " + e.getMessage());
                }
            }

            // BƯỚC 3: Lấy Carousel
            HttpSession session = request.getSession();
            List<Map<String, Object>> cachedAiSuggestions = 
                (List<Map<String, Object>>) session.getAttribute("aiSuggestions_" + query);
            List<Map<String, Object>> cachedRelatedMovies = 
                (List<Map<String, Object>>) session.getAttribute("relatedMovies_" + query);

            if (cachedAiSuggestions == null || cachedRelatedMovies == null) {
                if (!finalSearchResults.isEmpty() && !Boolean.TRUE.equals(finalSearchResults.get(0).get("isPerson"))) {
                    Integer tmdbIdFromMap = (Integer) finalSearchResults.get(0).get("tmdbId");
                    if (tmdbIdFromMap != null) {
                         topResultIdPage1 = tmdbIdFromMap;
                    } else {
                        topResultIdPage1 = (Integer) finalSearchResults.get(0).get("id");
                    }
                }

                List<Integer> excludeIds = new ArrayList<>(addedTmdbIds);
                cachedAiSuggestions = loadAiSuggestions(topResultIdPage1, excludeIds);
                cachedRelatedMovies = loadRelatedMovies(encodedQuery, topResultIdPage1, excludeIds);
                
                session.setAttribute("aiSuggestions_" + query, cachedAiSuggestions);
                session.setAttribute("relatedMovies_" + query, cachedRelatedMovies);
            }

            model.addAttribute("aiSuggestions", cachedAiSuggestions); 
            model.addAttribute("relatedMovies", cachedRelatedMovies);
            
            // === FIX BUG 2 ===
            // Đảm bảo danh sách không bao giờ vượt quá PAGE_SIZE (20)
            List<Map<String, Object>> paginatedResults = finalSearchResults.stream()
                                                            .limit(PAGE_SIZE)
                                                            .collect(Collectors.toList());

            model.addAttribute("searchResults", paginatedResults); 
            // =================
            
            model.addAttribute("query", query);
            
            // === FIX BUG 3 (Lỗi đếm "kkkkk") ===
            int apiTotalResults = paginationJson.optInt("total_results", 0);
            
            // Nếu là trang 1, tổng kết quả là số lượng TẤT CẢ phim tìm thấy (DB + API + Diễn viên)
            // Nếu là trang > 1, tổng kết quả là tổng của API
            int finalTotal = (page == 1) ? finalSearchResults.size() : apiTotalResults;
            
            // Nếu kết quả cuối cùng (paginatedResults) rỗng, thì set total = 0
            if (paginatedResults.isEmpty()) {
                finalTotal = 0;
            }

            model.addAttribute("totalResults", finalTotal);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", Math.min(paginationJson.optInt("total_pages", 1), 500)); 
            // =================
            
            model.addAttribute("hasResults", !finalSearchResults.isEmpty());
            
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
    
    // (Các hàm loadRelatedMovies, loadAiSuggestions, addMoviesToList, setEmptyResults giữ nguyên)
    
    private List<Map<String, Object>> loadRelatedMovies(String encodedQuery, Integer topResultId, List<Integer> excludeIds) {
        Set<Integer> addedIds = new HashSet<>(excludeIds);
        List<Map<String, Object>> movies = new ArrayList<>();
        int limit = 20;
        try {
            String searchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
            addMoviesToList(searchUrl, movies, addedIds, limit);
            if (movies.size() >= limit) return movies;
            if (topResultId != null) {
                String similarUrl = BASE_URL + "/movie/" + topResultId + "/similar?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(similarUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }
            String popularUrl = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=vi-VN&page=1";
            addMoviesToList(popularUrl, movies, addedIds, limit);
        } catch (Exception e) { System.err.println("Lỗi loadRelatedMovies: " + e.getMessage()); }
        return movies;
    }

    private List<Map<String, Object>> loadAiSuggestions(Integer topResultId, List<Integer> excludeIds) {
        Set<Integer> addedIds = new HashSet<>(excludeIds);
        List<Map<String, Object>> movies = new ArrayList<>();
        int limit = 20;
        try {
            if (topResultId != null) {
                String recommendUrl = BASE_URL + "/movie/" + topResultId + "/recommendations?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(recommendUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }
            if (topResultId != null) {
                String similarUrl = BASE_URL + "/movie/" + topResultId + "/similar?api_key=" + API_KEY + "&language=vi-VN&page=1";
                addMoviesToList(similarUrl, movies, addedIds, limit);
                if (movies.size() >= limit) return movies;
            }
            String trendingUrl = BASE_URL + "/trending/movie/week?api_key=" + API_KEY + "&language=vi-VN&page=1";
            addMoviesToList(trendingUrl, movies, addedIds, limit);
        } catch (Exception e) { System.err.println("Lỗi loadAiSuggestions: " + e.getMessage()); }
        return movies;
    }

    private void addMoviesToList(String apiUrl, List<Map<String, Object>> movies, Set<Integer> addedIds, int limit) {
        try {
            String response = restTemplate.getForObject(apiUrl, String.class);
            if (response == null) return;
            
            JSONArray results = new JSONObject(response).optJSONArray("results");
            if (results == null) return;
            
            for (int i = 0; i < Math.min(results.length(), limit); i++) {
                if (movies.size() >= limit) break;
                
                JSONObject item = results.getJSONObject(i);
                int tmdbId = item.optInt("id");
                if (tmdbId <= 0 || addedIds.contains(tmdbId)) continue;
                
                Movie movie = movieService.syncMovieFromList(item);
                
                if (movie != null) {
                    movies.add(movieService.convertToMap(movie));
                    addedIds.add(tmdbId);
                }
            }
        } catch (Exception e) {
             System.err.println("Lỗi addMoviesToList: " + e.getMessage());
        }
    }
    
    private void setEmptyResults(Model model, String query) {
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("aiSuggestions", new ArrayList<>());
        model.addAttribute("relatedMovies", new ArrayList<>());
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