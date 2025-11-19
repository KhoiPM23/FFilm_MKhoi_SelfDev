package com.example.project.controller;

import com.example.project.model.Genre;
import com.example.project.model.Movie;
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
import org.springframework.data.domain.Page;

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

    //---- 1. CẤU HÌNH & REPOSITORY ----

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private static final int PAGE_SIZE = 20;

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;

    //---- 2. MAIN SEARCH LOGIC ----

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

        int finalTotal = 0;
        int finalTotalPages = 1;
        HttpSession session = request.getSession();
        String sessionKey = "fullSearchResults_" + query;
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        List<Map<String, Object>> fullSearchResults;

        //----- Tải hoặc xây dựng kết quả thô (lưu vào Session)
        if (page == 1 || session.getAttribute(sessionKey) == null) {
            
            fullSearchResults = new ArrayList<>();
            Set<Integer> addedTmdbIds = new HashSet<>();

            //----- Bước 1: Kết quả từ DB (Luôn an toàn)
            try {
                List<Movie> dbResults = movieService.searchMoviesByTitle(query.trim());
                for (Movie movie : dbResults) {
                    fullSearchResults.add(movieService.convertToMap(movie));
                    if (movie.getTmdbId() != null) addedTmdbIds.add(movie.getTmdbId());
                }
            } catch (Exception e) {
                System.err.println("Lỗi Search DB: " + e.getMessage());
            }

            //----- Bước 2: Kết quả từ Person Search (Credits) - CÓ FALLBACK
            try {
                String personSearchUrl = BASE_URL + "/search/person?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
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
                        
                        // Lấy Cast
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
                        // Lấy Crew (Director)
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
                // [QUAN TRỌNG] Chỉ ghi log, KHÔNG throw lỗi để app không sập
                System.err.println("⚠️ Lỗi Person Search (API có thể mất kết nối): " + e.getMessage());
            }

            //----- Bước 3: Kết quả từ API Movie (3 trang) - CÓ FALLBACK
            try {
                for (int apiPage = 1; apiPage <= 3; apiPage++) {
                    String movieSearchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=" + apiPage + "&include_adult=false";
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
            } catch (Exception e) {
                // [QUAN TRỌNG] Chỉ ghi log, KHÔNG throw lỗi
                System.err.println("⚠️ Lỗi Movie Search API (API có thể mất kết nối): " + e.getMessage());
            }

            session.setAttribute(sessionKey, fullSearchResults);
        } else {
            //----- Lấy từ Session Cache
            fullSearchResults = (List<Map<String, Object>>) session.getAttribute(sessionKey);
            if (fullSearchResults == null) return "redirect:/search?query=" + encodedQuery;
        }

        //----- Bước 4: Áp dụng Filters (trên tập dữ liệu thô)
        List<Map<String, Object>> filteredResults = new ArrayList<>(fullSearchResults);

        if (genres != null && !genres.isEmpty()) {
            List<Integer> filterGenres = Stream.of(genres.split(",")).map(Integer::parseInt).collect(Collectors.toList());
            filteredResults.removeIf(movieMap -> {
                Movie movie = movieService.getMovieById((Integer) movieMap.get("id"));
                return movie.getGenres().stream().noneMatch(g -> filterGenres.contains(g.getTmdbGenreId()));
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

        //----- Bước 5: Phân trang (Pagination)
        int totalResults = filteredResults.size();
        int totalPages = (int) Math.ceil((double) totalResults / PAGE_SIZE);
        if (page > totalPages) page = (totalPages > 0) ? totalPages : 1;
        if (page < 1) page = 1;

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalResults);

        List<Map<String, Object>> paginatedResults = (start <= end) ? filteredResults.subList(start, end) : new ArrayList<>();

        finalTotal = totalResults;
        finalTotalPages = (totalPages > 0) ? totalPages : 1;


        //----- Bước 6: Xử lý 2 Carousel Gợi ý (Nếu có kết quả)
        List<Map<String, Object>> aiSuggestions = new ArrayList<>();
        List<Map<String, Object>> relatedMovies = new ArrayList<>();

        // Bọc trong try-catch để đảm bảo trang Search không bao giờ chết vì gợi ý
        try {
            if (!paginatedResults.isEmpty()) {
                // Lấy ID (PK) của các phim trong kết quả chính để loại trừ
                List<Integer> excludeIds = paginatedResults.stream().map(m -> (Integer) m.get("id")).collect(Collectors.toList());

                // Phân tích Top 5 (hoặc ít hơn)
                List<Map<String, Object>> topResults = paginatedResults.subList(0, Math.min(paginatedResults.size(), 5));
                
                // (List 1) Phân tích Genre cho Related Movies
                Integer topGenreId = analyzeTopGenre(topResults);
                
                // (List 2) Phân tích Intent cho AI Suggestions (Waterfall)
                Movie topResultMovie = movieService.getMovieById((Integer) topResults.get(0).get("id"));

                // Tải dữ liệu gợi ý
                aiSuggestions = loadAiSuggestions(topResultMovie, excludeIds);
                relatedMovies = loadRelatedMovies(topGenreId, encodedQuery, excludeIds);
            }
        } catch (Exception e) {
             System.err.println("Lỗi khi tạo Gợi ý (không ảnh hưởng kết quả chính): " + e.getMessage());
        }

        //----- Bước 7: Gán Model và Trả về
        model.addAttribute("searchResults", paginatedResults);
        model.addAttribute("query", query);
        model.addAttribute("totalResults", finalTotal);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", finalTotalPages);
        model.addAttribute("hasResults", !paginatedResults.isEmpty());
        model.addAttribute("aiSuggestions", aiSuggestions);
        model.addAttribute("relatedMovies", relatedMovies);
        
        // Gán lại filter params để khôi phục trạng thái
        model.addAttribute("selectedGenres", genres != null ? genres : "");
        model.addAttribute("yearFrom", yearFrom != null ? yearFrom : "");
        model.addAttribute("yearTo", yearTo != null ? yearTo : "");
        model.addAttribute("minRating", minRating != null ? minRating : "0");
        model.addAttribute("quickFilter", quickFilter != null ? quickFilter : "");
        
        return "search";
    }

    //---- 3. CAROUSEL / SUGGESTION HELPERS ----

    // Phân tích Genre từ Top 5 kết quả tìm kiếm
    private Integer analyzeTopGenre(List<Map<String, Object>> topResults) {
        if (topResults == null || topResults.isEmpty()) return null;

        try {
            //----- Đếm tần suất xuất hiện của các Genre ID
            Map<Integer, Long> genreCounts = topResults.stream()
                .map(movieMap -> movieService.getMovieById((Integer) movieMap.get("id")))
                .filter(Objects::nonNull)
                .flatMap(movie -> movie.getGenres().stream())
                .map(Genre::getTmdbGenreId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            //----- Tìm Genre ID xuất hiện nhiều nhất
            Optional<Map.Entry<Integer, Long>> maxEntry = genreCounts.entrySet().stream().max(Map.Entry.comparingByValue());

            return maxEntry.map(Map.Entry::getKey).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // List 1: Gợi ý theo THỂ LOẠI (Related Movies)
    private List<Map<String, Object>> loadRelatedMovies(Integer topGenreId, String encodedQuery, List<Integer> excludeIds) {
        int limit = 20;
        int dbFetchLimit = 40;
        
        String apiUrl;
        com.example.project.service.MovieService.SortBy sortBy = MovieService.SortBy.HOT;
        Page<Movie> dbMovies;

        try {
            if (topGenreId != null) {
                //----- Nguồn API: Phim cùng thể loại (Genre)
                apiUrl = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=vi-VN&with_genres=" + topGenreId + "&sort_by=popularity.desc";
                //----- Nguồn DB: Phim cùng thể loại
                dbMovies = movieService.getMoviesByGenreFromDB(topGenreId, dbFetchLimit, 0);
            } else {
                //----- Fallback API: API Search (dùng query)
                apiUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
                //----- Fallback DB: Hot DB
                dbMovies = movieService.getHotMoviesFromDB(dbFetchLimit);
            }
            
            List<Map<String, Object>> mergedMovies = movieService.getMergedCarouselMovies(apiUrl, dbMovies, limit, sortBy);

            // Lọc bỏ các ID đã hiển thị trong kết quả chính
            return mergedMovies.stream()
                .filter(m -> !excludeIds.contains((Integer) m.get("id")))
                .collect(Collectors.toList());
        } catch (Exception e) {
             return new ArrayList<>(); // Trả về rỗng nếu lỗi
        }
    }

    // List 2: Gợi ý theo INTENT (AI Suggestions / Waterfall 5 Lớp)
    private List<Map<String, Object>> loadAiSuggestions(Movie topResultMovie, List<Integer> excludeIds) {
        int limit = 20;
        if (topResultMovie == null) return new ArrayList<>();

        try {
            //----- Gọi hàm Waterfall mới từ MovieService
            List<Map<String, Object>> recommendedMovies = movieService.getRecommendedMoviesWaterfall(topResultMovie, new HashMap<>());

            // Lọc bỏ các ID đã hiển thị trong kết quả chính
            return recommendedMovies.stream()
                .filter(m -> !excludeIds.contains((Integer) m.get("id")))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
             return new ArrayList<>(); // Trả về rỗng nếu lỗi
        }
    }

    //---- 4. UTILS ----

    // Đặt các thuộc tính mặc định cho trang rỗng
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