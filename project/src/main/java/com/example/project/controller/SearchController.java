package com.example.project.controller;

import com.example.project.model.Genre; 
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
import java.util.Collections; 
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects; 
import java.util.Optional; 
import java.util.Set;
import java.util.function.Function; 
import java.util.stream.Collectors;
import java.util.stream.Stream; 

@Controller
public class SearchController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
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
            @RequestParam(required = false) String quickFilter, // Giữ lại, VĐ 7 gác lại
            HttpServletRequest request,
            Model model) {
        
        if (query == null || query.trim().isEmpty()) {
            setEmptyResults(model, null);
            return "search";
        }

        // [SỬA LỖI image_59997a.png] Khai báo biến ở scope ngoài
        int finalTotal = 0;
        int finalTotalPages = 1;
        HttpSession session = request.getSession();
        String sessionKey = "fullSearchResults_" + query; // Key để lưu cache

        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            
            List<Map<String, Object>> fullSearchResults;

            if (page == 1 || session.getAttribute(sessionKey) == null) {
                fullSearchResults = new ArrayList<>();
                Set<Integer> addedTmdbIds = new HashSet<>();

                // 1. DB
                List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
                for (Movie movie : dbResults) {
                    fullSearchResults.add(movieService.convertToMap(movie));
                    if (movie.getTmdbId() != null) addedTmdbIds.add(movie.getTmdbId());
                }

                // 2. Person (ĐỒNG BỘ VỚI JS)
                try {
                    String personSearchUrl = BASE_URL + "/search/person?api_key=" + API_KEY +
                                            "&language=vi-VN&query=" + encodedQuery + "&page=1";
                    String personResponse = restTemplate.getForObject(personSearchUrl, String.class);
                    JSONArray personResults = new JSONObject(personResponse).optJSONArray("results");

                    if (personResults != null) {
                        for (int i = 0; i < Math.min(personResults.length(), 10); i++) {
                            JSONObject person = personResults.getJSONObject(i);
                            int personId = person.optInt("id");
                            if (personId <= 0) continue;

                            String creditsUrl = BASE_URL + "/person/" + personId + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
                            String creditsResponse = restTemplate.getForObject(creditsUrl, String.class);
                            JSONObject creditsJson = new JSONObject(creditsResponse);
                            
                            String personName = person.optString("name");
                            
                            // Cast
                            JSONArray castMovies = creditsJson.optJSONArray("cast");
                            if (castMovies != null) {
                                for (int j = 0; j < Math.min(castMovies.length(), 10); j++) {
                                    JSONObject movieCredit = castMovies.getJSONObject(j);
                                    int movieTmdbId = movieCredit.optInt("id");
                                    if (movieTmdbId > 0 && !addedTmdbIds.contains(movieTmdbId)) {
                                        Movie movie = movieService.syncMovieFromList(movieCredit);
                                        if (movie != null) {
                                            fullSearchResults.add(movieService.convertToMap(movie, "Diễn viên: " + personName));
                                            addedTmdbIds.add(movieTmdbId);
                                        }
                                    }
                                }
                            }
                            
                            // Crew (Director)
                            JSONArray crewMovies = creditsJson.optJSONArray("crew");
                            if (crewMovies != null) {
                                int directorCount = 0;
                                for (int j = 0; j < crewMovies.length() && directorCount < 10; j++) {
                                    JSONObject movieCredit = crewMovies.getJSONObject(j);
                                    if (!"Director".equals(movieCredit.optString("job"))) continue;
                                    
                                    int movieTmdbId = movieCredit.optInt("id");
                                    if (movieTmdbId > 0 && !addedTmdbIds.contains(movieTmdbId)) {
                                        Movie movie = movieService.syncMovieFromList(movieCredit);
                                        if (movie != null) {
                                            fullSearchResults.add(movieService.convertToMap(movie, "Đạo diễn: " + personName));
                                            addedTmdbIds.add(movieTmdbId);
                                            directorCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi Person search: " + e.getMessage());
                }

                // 3. API Movie (3 trang)
                for (int apiPage = 1; apiPage <= 3; apiPage++) {
                    String movieSearchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY +
                                    "&language=vi-VN&query=" + encodedQuery +
                                    "&page=" + apiPage + "&include_adult=false";
                                    
                    String movieResponse = restTemplate.getForObject(movieSearchUrl, String.class);
                    JSONObject paginationJson = new JSONObject(movieResponse);
                    JSONArray movieResults = paginationJson.optJSONArray("results");

                    if (movieResults != null && movieResults.length() > 0) {
                        for (int i = 0; i < movieResults.length(); i++) {
                            JSONObject item = movieResults.getJSONObject(i);
                            int tmdbId = item.optInt("id", -1);
                            
                            if (tmdbId > 0 && !addedTmdbIds.contains(tmdbId)) {
                                Movie movie = movieService.syncMovieFromList(item);
                                if (movie != null) {
                                    fullSearchResults.add(movieService.convertToMap(movie));
                                    addedTmdbIds.add(tmdbId);
                                }
                            }
                        }
                    } else break;
                }
                
                session.setAttribute(sessionKey, fullSearchResults);
            } else {
                fullSearchResults = (List<Map<String, Object>>) session.getAttribute(sessionKey);
                if (fullSearchResults == null) {
                    return "redirect:/search?query=" + encodedQuery;
                }
            }

            // ✅ DEBUG: In ra filter params
            System.out.println("=== FILTER PARAMS ===");
            System.out.println("Genres: " + genres);
            System.out.println("YearFrom: " + yearFrom);
            System.out.println("YearTo: " + yearTo);
            System.out.println("MinRating: " + minRating);
            System.out.println("QuickFilter: " + quickFilter);
            System.out.println("FullSearchResults size: " + fullSearchResults.size());
            System.out.println("=====================");

            // [SỬA LỖI VĐ 1 & 7] ÁP DỤNG FILTER (NẾU CÓ)
            // Logic lọc này sẽ chạy trên TẤT CẢ KẾT QUẢ (đã lấy từ Session)
            List<Map<String, Object>> filteredResults = new ArrayList<>(fullSearchResults);
            
            if (genres != null && !genres.isEmpty()) {
                List<Integer> filterGenres = Stream.of(genres.split(","))
                    .map(Integer::parseInt).collect(Collectors.toList());
                filteredResults.removeIf(movieMap -> {
                    Movie movie = movieService.getMovieById((Integer) movieMap.get("id"));
                    return movie.getGenres().stream()
                        .noneMatch(g -> filterGenres.contains(g.getTmdbGenreId()));
                });
            }
            if (yearFrom != null && !yearFrom.isEmpty()) {
                int from = Integer.parseInt(yearFrom);
                filteredResults.removeIf(m -> m.get("year") == null || m.get("year").equals("N/A") || Integer.parseInt((String)m.get("year")) < from);
            }
            if (yearTo != null && !yearTo.isEmpty()) {
                int to = Integer.parseInt(yearTo);
                filteredResults.removeIf(m -> m.get("year") == null || m.get("year").equals("N/A") || Integer.parseInt((String)m.get("year")) > to);
            }
            if (minRating != null && !minRating.isEmpty()) {
                double min = Double.parseDouble(minRating);
                if (min > 0) {
                    filteredResults.removeIf(m -> m.get("rating") == null || Double.parseDouble((String)m.get("rating")) < min);
                }
            }
            
            // [SỬA LỖI VĐ 1] PHÂN TRANG (PAGINATION)
            // Tính toán dựa trên danh sách ĐÃ LỌC
            int totalResults = filteredResults.size();
            int totalPages = (int) Math.ceil((double) totalResults / PAGE_SIZE);
            if (page > totalPages) page = (totalPages > 0) ? totalPages : 1;
            if (page < 1) page = 1;

            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalResults);
            
            List<Map<String, Object>> paginatedResults = (start <= end) ? filteredResults.subList(start, end) : new ArrayList<>();
            
            // [SỬA LỖI VĐ 1 & image_59997a.png] Gán giá trị
            finalTotal = totalResults;
            finalTotalPages = (totalPages > 0) ? totalPages : 1;

            // 6. XỬ LÝ 2 CAROUSEL GỢI Ý (KẾ HOẠCH C)
            
            // [Constraint 1] Ẩn nếu rỗng
            if (paginatedResults.isEmpty()) {
                model.addAttribute("aiSuggestions", new ArrayList<>());
                model.addAttribute("relatedMovies", new ArrayList<>());
            } else {
                // Lấy ID (PK) của các phim trong kết quả chính để loại trừ
                List<Integer> excludeIds = paginatedResults.stream()
                                            .map(m -> (Integer) m.get("id"))
                                            .collect(Collectors.toList());

                // [Constraint 2] Phân tích Top 5 (hoặc ít hơn)
                List<Map<String, Object>> topResults = paginatedResults.subList(0, Math.min(paginatedResults.size(), 5));
                
                // (List 1) Phân tích Genre
                Integer topGenreId = analyzeTopGenre(topResults);
                
                // (List 2) Phân tích Intent
                Movie topResultMovie = movieService.getMovieById((Integer) topResults.get(0).get("id"));

                // (Tải dữ liệu)
                model.addAttribute("aiSuggestions", loadAiSuggestions(topResultMovie, excludeIds));
                model.addAttribute("relatedMovies", loadRelatedMovies(topGenreId, encodedQuery, excludeIds));
            }
            
            // 7. GÁN MODEL (Như cũ)
            model.addAttribute("searchResults", paginatedResults);
            model.addAttribute("query", query);
            
            model.addAttribute("totalResults", finalTotal);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", finalTotalPages);
            
            model.addAttribute("hasResults", !paginatedResults.isEmpty());
            
            model.addAttribute("selectedGenres", genres != null ? genres : "");
            model.addAttribute("yearFrom", yearFrom != null ? yearFrom : "");
            model.addAttribute("yearTo", yearTo != null ? yearTo : "");
            model.addAttribute("minRating", minRating != null ? minRating : "0");
            model.addAttribute("quickFilter", quickFilter != null ? quickFilter : "");
            
            return "search";
            
        } catch (Exception e) {
            e.printStackTrace();
            session.removeAttribute(sessionKey); // Xóa cache nếu có lỗi
            setEmptyResults(model, query);
            return "search";
        }
    }
    
    /**
     * [MỚI - KẾ HOẠCH C] Helper: Phân tích Genre từ Top 5
     */
    private Integer analyzeTopGenre(List<Map<String, Object>> topResults) {
        if (topResults == null || topResults.isEmpty()) {
            return null;
        }
        
        // Đếm tần suất xuất hiện của các Genre ID
        Map<Integer, Long> genreCounts = topResults.stream()
            .map(movieMap -> movieService.getMovieById((Integer) movieMap.get("id"))) // Lấy Movie object
            .flatMap(movie -> movie.getGenres().stream()) // Lấy list Genre
            .map(Genre::getTmdbGenreId) // Lấy tmdbGenreId
            .filter(Objects::nonNull) // Bỏ qua genre null
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())); // Đếm

        // Tìm Genre ID xuất hiện nhiều nhất
        Optional<Map.Entry<Integer, Long>> maxEntry = genreCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue());

        return maxEntry.map(Map.Entry::getKey).orElse(null); // Trả về ID (vd: 28) hoặc null
    }

    /**
     * [VIẾT LẠI - KẾ HOẠCH C] List 1: Gợi ý theo THỂ LOẠI (Genre)
     */
    private List<Map<String, Object>> loadRelatedMovies(Integer topGenreId, String encodedQuery, List<Integer> excludeIds) {
        int limit = 20;
        int dbFetchLimit = 40;
        
        String apiUrl;
        Page<Movie> dbMovies;
        MovieService.SortBy sortBy = MovieService.SortBy.HOT;

        if (topGenreId != null) {
            // (1A) Nguồn API: Phim cùng thể loại
            apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=" + topGenreId + "&sort_by=popularity.desc";
            // (1B) Nguồn DB: Phim cùng thể loại
            dbMovies = movieService.getMoviesByGenreFromDB(topGenreId, dbFetchLimit, 0);
        } else {
            // (2A) Fallback API: Dùng API Search (như cũ)
            apiUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
            // (2B) Fallback DB: Dùng Hot DB (lỗi cũ, nhưng là fallback)
            dbMovies = movieService.getHotMoviesFromDB(dbFetchLimit);
        }
        
        List<Map<String, Object>> mergedMovies = movieService.getMergedCarouselMovies(
            apiUrl, 
            dbMovies, 
            limit, 
            sortBy
        );

        // Lọc bỏ các ID đã hiển thị trong kết quả chính
        return mergedMovies.stream()
            .filter(m -> {
                Integer pkId = (Integer) m.get("id"); // Luôn dùng PK (id) để loại trừ
                return !excludeIds.contains(pkId);
            })
            .collect(Collectors.toList());
    }

    /**
     * [VIẾT LẠI - KẾ HOẠCH C] List 2: Gợi ý theo INTENT (Waterfall 5 Lớp)
     */
    private List<Map<String, Object>> loadAiSuggestions(Movie topResultMovie, List<Integer> excludeIds) {
        int limit = 20;
        if (topResultMovie == null) return new ArrayList<>(); // An toàn

        // Gọi thẳng hàm Waterfall 5 Lớp từ MovieService (đã có sẵn)
        List<Map<String, Object>> recommendedMovies = movieService.getRecommendedMoviesWaterfall(
            topResultMovie, 
            new HashMap<>() // (Truyền Map rỗng, không cần lấy title)
        );

        // Lọc bỏ các ID đã hiển thị trong kết quả chính
        return recommendedMovies.stream()
            .filter(m -> {
                Integer pkId = (Integer) m.get("id"); // Luôn dùng PK (id) để loại trừ
                return !excludeIds.contains(pkId);
            })
            .limit(limit) // Giới hạn cuối cùng
            .collect(Collectors.toList());
    }
    
    // (Hàm setEmptyResults giữ nguyên)
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