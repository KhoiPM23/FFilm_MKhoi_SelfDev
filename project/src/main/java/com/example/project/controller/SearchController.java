package com.example.project.controller;

import com.example.project.model.Genre;
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
            @RequestParam(required = false) Boolean isFree,
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

            //----- Bước 1: Kết quả từ DB (SỬ DỤNG LOGIC MỚI - TỐI ƯU HÓA)
            try {
                String cleanQuery = query.trim();
                
                // [FIX] Gọi hàm searchMoviesCombined từ Service 
                // Hàm này đã bao gồm: 
                // 1. Tìm theo tên phim
                // 2. Tìm theo tên diễn viên (tự động lấy Role chuẩn từ bảng MoviePerson: "Tony Stark" thay vì "Diễn viên")
                List<Map<String, Object>> dbResults = movieService.searchMoviesCombined(cleanQuery);
                
                // Add vào danh sách kết quả chung
                fullSearchResults.addAll(dbResults);
                
                // Lưu TMDB ID để tránh trùng lặp khi gọi API ở Bước 2 & 3 (Fallback)
                for (Map<String, Object> item : dbResults) {
                    Object tmdbIdObj = item.get("tmdbId");
                    if (tmdbIdObj != null) {
                        // Xử lý an toàn cho kiểu dữ liệu Integer/String
                        if (tmdbIdObj instanceof Integer) {
                            addedTmdbIds.add((Integer) tmdbIdObj);
                        } else {
                            try {
                                addedTmdbIds.add(Integer.parseInt(tmdbIdObj.toString()));
                            } catch (NumberFormatException e) { /* Ignore */ }
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("Lỗi truy vấn DB: " + e.getMessage());
            }

            // //----- Bước 2: Kết quả từ Person Search (Credits) - CÓ FALLBACK
            // try {
            //     String personSearchUrl = BASE_URL + "/search/person?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=1";
            //     String personResponse = restTemplate.getForObject(personSearchUrl, String.class);
            //     JSONArray personResults = new JSONObject(personResponse).optJSONArray("results");

            //     if (personResults != null) {
            //         for (int i = 0; i < Math.min(personResults.length(), 10); i++) {
            //             JSONObject person = personResults.getJSONObject(i);
            //             int personId = person.optInt("id");
            //             if (personId <= 0) continue;

            //             String creditsUrl = BASE_URL + "/person/" + personId + "/movie_credits?api_key=" + API_KEY + "&language=vi-VN";
            //             String creditsResponse = restTemplate.getForObject(creditsUrl, String.class);
            //             JSONObject creditsJson = new JSONObject(creditsResponse);
            //             String personName = person.optString("name");
                        
            //             // Lấy Cast
            //             JSONArray castMovies = creditsJson.optJSONArray("cast");
            //             if (castMovies != null) {
            //                 for (int j = 0; j < Math.min(castMovies.length(), 10); j++) {
            //                     JSONObject movieCredit = castMovies.getJSONObject(j);
            //                     int movieTmdbId = movieCredit.optInt("id");
            //                     if (movieTmdbId > 0 && !addedTmdbIds.contains(movieTmdbId)) {
            //                         Movie movie = movieService.syncMovieFromList(movieCredit);
            //                         if (movie != null) {
            //                             fullSearchResults.add(movieService.convertToMap(movie, "Diễn viên: " + personName));
            //                             addedTmdbIds.add(movieTmdbId);
            //                         }
            //                     }
            //                 }
            //             }
            //             // Lấy Crew (Director)
            //             JSONArray crewMovies = creditsJson.optJSONArray("crew");
            //             if (crewMovies != null) {
            //                 int directorCount = 0;
            //                 for (int j = 0; j < crewMovies.length() && directorCount < 10; j++) {
            //                     JSONObject movieCredit = crewMovies.getJSONObject(j);
            //                     if (!"Director".equals(movieCredit.optString("job"))) continue;

            //                     int movieTmdbId = movieCredit.optInt("id");
            //                     if (movieTmdbId > 0 && !addedTmdbIds.contains(movieTmdbId)) {
            //                         Movie movie = movieService.syncMovieFromList(movieCredit);
            //                         if (movie != null) {
            //                             fullSearchResults.add(movieService.convertToMap(movie, "Đạo diễn: " + personName));
            //                             addedTmdbIds.add(movieTmdbId);
            //                             directorCount++;
            //                         }
            //                     }
            //                 }
            //             }
            //         }
            //     }
            // } catch (Exception e) {
            //     // [QUAN TRỌNG] Chỉ ghi log, KHÔNG throw lỗi để app không sập
            //     System.err.println("⚠️ Lỗi Person Search (API có thể mất kết nối): " + e.getMessage());
            // }

            // //----- Bước 3: Kết quả từ API Movie (3 trang) - CÓ FALLBACK
            // try {
            //     for (int apiPage = 1; apiPage <= 3; apiPage++) {
            //         String movieSearchUrl = BASE_URL + "/search/movie?api_key=" + API_KEY + "&language=vi-VN&query=" + encodedQuery + "&page=" + apiPage + "&include_adult=false";
            //         String movieResponse = restTemplate.getForObject(movieSearchUrl, String.class);
            //         JSONObject paginationJson = new JSONObject(movieResponse);
            //         JSONArray movieResults = paginationJson.optJSONArray("results");

            //         if (movieResults != null && movieResults.length() > 0) {
            //             for (int i = 0; i < movieResults.length(); i++) {
            //                 JSONObject item = movieResults.getJSONObject(i);
            //                 int tmdbId = item.optInt("id", -1);

            //                 if (tmdbId > 0 && !addedTmdbIds.contains(tmdbId)) {
            //                     Movie movie = movieService.syncMovieFromList(item);
            //                     if (movie != null) {
            //                         fullSearchResults.add(movieService.convertToMap(movie));
            //                         addedTmdbIds.add(tmdbId);
            //                     }
            //                 }
            //             }
            //         } else break;
            //     }
            // } catch (Exception e) {
            //     // [QUAN TRỌNG] Chỉ ghi log, KHÔNG throw lỗi
            //     System.err.println("⚠️ Lỗi Movie Search API (API có thể mất kết nối): " + e.getMessage());
            // }

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

        // [MỚI] Lọc theo Free/Paid
        if (isFree != null) {
            filteredResults.removeIf(m -> {
                Object freeVal = m.get("isFree");
                if (freeVal == null) return true; // Bỏ qua nếu null
                return !freeVal.equals(isFree);
            });
        }

        //----- Bước 4.5: Sắp xếp (Sorting) theo Quick Filter
        if ("new".equals(quickFilter)) {
            // Mới nhất (Release Date giảm dần)
            filteredResults.sort((m1, m2) -> {
                String d1 = (String) m1.getOrDefault("releaseDate", "");
                String d2 = (String) m2.getOrDefault("releaseDate", "");
                return d2.compareTo(d1); 
            });
        } else if ("top-rated".equals(quickFilter)) {
            // Đánh giá cao (Rating giảm dần)
            filteredResults.sort((m1, m2) -> {
                float r1 = m1.containsKey("rating_raw") ? (float) m1.get("rating_raw") : 0f;
                float r2 = m2.containsKey("rating_raw") ? (float) m2.get("rating_raw") : 0f;
                return Float.compare(r2, r1);
            });
        } else if ("trending".equals(quickFilter)) {
            // Thịnh hành (Popularity giảm dần)
            filteredResults.sort((m1, m2) -> {
                double p1 = (double) m1.getOrDefault("popularity", 0.0);
                double p2 = (double) m2.getOrDefault("popularity", 0.0);
                return Double.compare(p2, p1);
            });
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

        try {
            List<Integer> excludeIds = new ArrayList<>();
            List<Map<String, Object>> sourceForRelated = new ArrayList<>();
            Movie sourceForAI = null;

            if (!paginatedResults.isEmpty()) {
                excludeIds = paginatedResults.stream().map(m -> (Integer) m.get("id")).collect(Collectors.toList());
                
                // Lấy nguồn dữ liệu
                sourceForRelated = paginatedResults.subList(0, Math.min(paginatedResults.size(), 5));
                sourceForAI = movieService.getMovieById((Integer) sourceForRelated.get(0).get("id"));
            } 
            
            // 1. RELATED MOVIES (Đã có logic Backfill mạnh mẽ trong Service)
            relatedMovies = movieService.getRelatedMoviesFromList(sourceForRelated, 20);
            
            // 2. AI SUGGESTIONS (Waterfall + Fallback)
            if (sourceForAI != null) {
                Map<String, Object> context = new HashMap<>();
                // Gọi Waterfall
                List<Map<String, Object>> rawSuggestions = movieService.getRecommendedMoviesWaterfall(sourceForAI, context);
                
                // Lọc trùng thủ công tại Controller để đảm bảo sạch sẽ
                Set<Integer> usedIds = new HashSet<>(excludeIds);
                // Thêm cả các ID đã xuất hiện trong Related Movies vào danh sách loại trừ
                for(Map<String, Object> m : relatedMovies) usedIds.add((Integer)m.get("id"));

                for (Map<String, Object> m : rawSuggestions) {
                    if (usedIds.add((Integer) m.get("id"))) {
                        aiSuggestions.add(m);
                    }
                }
            }
            
            // [CHỐT CHẶN CUỐI CÙNG] Nếu AI Suggestion vẫn rỗng (do bị lọc hết), gọi Fallback cưỡng bức
            if (aiSuggestions.isEmpty()) {
                Set<Integer> finalExcludes = new HashSet<>(excludeIds);
                for(Map<String, Object> m : relatedMovies) finalExcludes.add((Integer)m.get("id"));
                
                // Gọi hàm fallback lấy phim Hot/New
                aiSuggestions = movieService.loadRecommendedFallback(null, finalExcludes, 20);
            }

        } catch (Exception e) {
             System.err.println("Lỗi tạo Gợi ý: " + e.getMessage());
             // Fallback an toàn khi lỗi: Lấy phim Hot bất chấp
             try {
                 aiSuggestions = movieService.loadRecommendedFallback(null, new HashSet<>(), 20);
             } catch (Exception ex) {}
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
        model.addAttribute("isFree", isFree);
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

    // List 1: Gợi ý theo GROUP (Phim liên quan)
    private List<Map<String, Object>> loadRelatedMovies(List<Map<String, Object>> topResults, List<Integer> excludeIds) {
        // [SỬA ĐỔI] Truyền toàn bộ list Top 5 vào Service để phân tích
        return movieService.getRelatedMoviesFromList(topResults, 20);
    }

    // List 2: Gợi ý theo INTENT (AI Suggestions - Waterfall Top 1)
    private List<Map<String, Object>> loadAiSuggestions(Movie topResultMovie, List<Integer> excludeIds) {
        if (topResultMovie == null) return new ArrayList<>();
        try {
            // Giữ nguyên Waterfall cho Top 1 (Chính xác cao)
            Map<String, Object> context = new HashMap<>();
            List<Map<String, Object>> results = movieService.getRecommendedMoviesWaterfall(topResultMovie, context);
            
            // Lọc ID trùng
            return results.stream()
                .filter(m -> !excludeIds.contains((Integer) m.get("id")))
                .collect(Collectors.toList());
        } catch (Exception e) { return new ArrayList<>(); }
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