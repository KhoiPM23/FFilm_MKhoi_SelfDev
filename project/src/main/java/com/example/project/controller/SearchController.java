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

@Controller
public class SearchController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    
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
            HttpServletRequest request, // Thêm HttpServletRequest
            Model model) {
        
        if (query == null || query.trim().isEmpty()) {
            setEmptyResults(model, null);
            return "search";
        }
        
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            
            List<Map<String, Object>> finalSearchResults = new ArrayList<>();
            Set<Integer> addedTmdbIds = new HashSet<>();
            Integer topResultIdPage1 = null;

            // [LOGIC MỚI] BƯỚC 1: Tìm trong Database (DB) của bạn TRƯỚC.
            // Chỉ chạy khi ở trang 1 để ưu tiên hiển thị.
            if (page == 1) {
                // Dùng hàm searchMoviesByTitle (đã có trong service của bạn)
                List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
                for (Movie movie : dbResults) {
                    finalSearchResults.add(movieService.convertToMap(movie));
                    if (movie.getTmdbId() != null) {
                        addedTmdbIds.add(movie.getTmdbId());
                    }
                }
            }

            // [LOGIC MỚI] BƯỚC 2: Tìm trên TMDB API
            String searchUrl = BASE_URL + "/search/multi?api_key=" + API_KEY + 
                               "&language=vi-VN&query=" + encodedQuery + 
                               "&page=" + page + "&include_adult=false";
                               
            String response = restTemplate.getForObject(searchUrl, String.class);
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results");

            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String mediaType = item.optString("media_type", "movie");
                    int tmdbId = item.optInt("id", -1);
                    
                    if (mediaType.equals("movie") || mediaType.equals("tv")) {
                        // [LOGIC QUAN TRỌNG] Chỉ thêm nếu ID này CHƯA có trong danh sách
                        if (tmdbId > 0 && !addedTmdbIds.contains(tmdbId)) {
                            // Dùng getMoviePartial để sync (nó sẽ tự kiểm tra và trả về bản DB nếu có)
                            Movie movie = movieService.getMoviePartial(tmdbId); 
                            
                            if (movie != null) {
                                finalSearchResults.add(movieService.convertToMap(movie));
                                addedTmdbIds.add(movie.getTmdbId()); 
                            }
                        }
                    }
                    else if (mediaType.equals("person")) {
                        // Logic diễn viên giữ nguyên
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
                            finalSearchResults.add(personAsMovie);
                        }
                    }
                }
            }

            // [LOGIC MỚI] BƯỚC 3: Lấy Carousel (Dùng logic cache của Claude)
            HttpSession session = request.getSession();
            List<Map<String, Object>> cachedAiSuggestions = 
                (List<Map<String, Object>>) session.getAttribute("aiSuggestions_" + query);
            List<Map<String, Object>> cachedRelatedMovies = 
                (List<Map<String, Object>>) session.getAttribute("relatedMovies_" + query);

            if (cachedAiSuggestions == null || cachedRelatedMovies == null) {
                // Lấy ID đầu tiên từ kết quả ĐÃ HỢP NHẤT
                if (!finalSearchResults.isEmpty() && !Boolean.TRUE.equals(finalSearchResults.get(0).get("isPerson"))) {
                    topResultIdPage1 = (Integer) finalSearchResults.get(0).get("id");
                }

                // Dùng hàm addMoviesToList (đã sửa của Claude) để tải carousel (chỉ dùng syncMovieFromList)
                List<Integer> excludeIds = new ArrayList<>(addedTmdbIds);
                cachedAiSuggestions = loadAiSuggestions(topResultIdPage1, excludeIds);
                cachedRelatedMovies = loadRelatedMovies(encodedQuery, topResultIdPage1, excludeIds);
                
                session.setAttribute("aiSuggestions_" + query, cachedAiSuggestions);
                session.setAttribute("relatedMovies_" + query, cachedRelatedMovies);
            }

            model.addAttribute("aiSuggestions", cachedAiSuggestions); 
            model.addAttribute("relatedMovies", cachedRelatedMovies);
            
            // [SỬA] Gửi danh sách KẾT QUẢ CUỐI CÙNG (đã lọc) ra view
            model.addAttribute("searchResults", finalSearchResults); 
            
            model.addAttribute("query", query);
            model.addAttribute("totalResults", json.optInt("total_results", 0));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", Math.min(json.optInt("total_pages", 1), 500));
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
    
    // (Các hàm loadRelatedMovies, loadAiSuggestions, setEmptyResults giữ nguyên)
    
    private List<Map<String, Object>> loadRelatedMovies(String encodedQuery, Integer topResultId, List<Integer> excludeIds) {
        Set<Integer> addedIds = new HashSet<>(excludeIds);
        List<Map<String, Object>> movies = new ArrayList<>();
        int limit = 10;
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
        int limit = 10;
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

    /**
     * [SỬA] Dùng hàm syncMovieFromList (Lazy) cho carousel
     * (Đây là logic của Claude, nó nhanh hơn)
     */
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
                
                // Dùng hàm LAZY (syncMovieFromList) cho carousel
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