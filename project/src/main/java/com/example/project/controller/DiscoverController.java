package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Controller
public class DiscoverController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";

    @Autowired
    private MovieService movieService; 

    @Autowired
    private RestTemplate restTemplate; 

    @GetMapping("/discover")
    public String discover(
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String quickFilter,
            Model model) {
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String threeMonthsAgo = LocalDate.now().minusMonths(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        List<Map<String, Object>> moviesForTemplate = new ArrayList<>();
        
        int totalApiResults = 0;
        int totalApiPages = 1;
        boolean hasResults = false;

        try {
            String discoverUrl = buildDiscoverUrl(page, genres, quickFilter, today, threeMonthsAgo);
            String response = restTemplate.getForObject(discoverUrl, String.class);
            
            if (response == null || response.isEmpty()) {
                setEmptyResults(model, genres, quickFilter);
                return "discover"; 
            }
            
            JSONObject json = new JSONObject(response);
            JSONArray results = json.optJSONArray("results"); 
            totalApiResults = json.optInt("total_results", 0);
            totalApiPages = json.optInt("total_pages", 1);
            
            // ----- SỬA LỖI P5 (Đảm bảo 20 phim ĐƯỢC LƯU) -----
            if (results != null && results.length() > 0) {
                
                for (int i = 0; i < results.length(); i++) { 
                    JSONObject item = results.getJSONObject(i);
                    int tmdbId = item.optInt("id");
                    if (tmdbId <= 0) continue; 

                    Movie syncedMovie = null;
                    try {
                        // 1. GỌI PHƯƠNG THỨC MỚI
                        // Phương thức này SẼ LƯU phim partial ngay cả khi fetch detail lỗi
                        syncedMovie = movieService.syncMovieFromTmdbData(item);
                        
                    } catch (Exception e) {
                        // Catch này chỉ bắt lỗi nghiêm trọng (vd: mất kết nối DB)
                        // Lỗi 404 của TMDB đã được service xử lý nội bộ
                        System.err.println("CRITICAL SYNC ERROR (P5): Không thể lưu movie (TMDB ID: " + tmdbId + "): " + e.getMessage());
                        // syncedMovie sẽ là null, chúng ta fallback hiển thị dữ liệu thô
                    }

                    // 3. Tạo Map để hiển thị
                    Map<String, Object> map = new HashMap<>();
                    
                    if (syncedMovie != null) {
                        // TRƯỜNG HỢP 1: Phim đã có hoặc VỪA SYNC THÀNH CÔNG (full hoặc partial)
                        // Luôn dùng `syncedMovie` (từ DB) để hiển thị
                        map.put("id", syncedMovie.getTmdbId()); 
                        map.put("title", syncedMovie.getTitle());
                        
                        String posterUrl = "/images/placeholder.jpg";
                        if(syncedMovie.getPosterPath() != null && !syncedMovie.getPosterPath().isEmpty()) {
                            if (syncedMovie.getPosterPath().startsWith("http")) {
                                posterUrl = syncedMovie.getPosterPath();
                            } else {
                                posterUrl = IMAGE_BASE_URL + "/w500" + syncedMovie.getPosterPath();
                            }
                        }
                        map.put("poster", posterUrl);
                        map.put("rating", String.format("%.1f", syncedMovie.getRating()));
                        map.put("year", syncedMovie.getReleaseDate() != null ? new SimpleDateFormat("yyyy").format(syncedMovie.getReleaseDate()) : "");
                        map.put("overview", syncedMovie.getDescription());
                    } else {
                        // TRƯỜNG HỢP 2: Phim lỗi sync NGHIÊM TRỌNG (vd: Lỗi DB)
                        // Hiển thị dữ liệu thô (giống lần trước)
                        map.put("id", tmdbId); 
                        map.put("title", item.optString("title", "N/A"));
                        String posterPath = item.optString("poster_path", "");
                        map.put("poster", posterPath.isEmpty() ? "/images/placeholder.jpg" : IMAGE_BASE_URL + "/w500" + posterPath);
                        map.put("rating", String.format("%.1f", item.optDouble("vote_average", 0.0)));
                        String releaseDate = item.optString("release_date", "");
                        map.put("year", releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "");
                        map.put("overview", item.optString("overview", ""));
                    }
                    
                    moviesForTemplate.add(map);
                }
            } // ----- KẾT THÚC SỬA LỖI P5 -----

            hasResults = !moviesForTemplate.isEmpty();

        } catch (Exception e) {
            System.err.println("Discover error: " + e.getMessage());
            e.printStackTrace();
            setEmptyResults(model, genres, quickFilter); 
            model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "discover"; 
        }

        // 4. Đẩy dữ liệu ra Model (Giữ nguyên)
        int totalUserPages = Math.min(totalApiPages, 500); 

        model.addAttribute("searchResults", moviesForTemplate); 
        model.addAttribute("pageTitle", getPageTitle(genres, quickFilter)); 
        model.addAttribute("totalResults", totalApiResults); 
        model.addAttribute("currentPage", page); 
        model.addAttribute("totalPages", totalUserPages); 
        model.addAttribute("hasResults", hasResults);
        
        model.addAttribute("genres", genres);
        model.addAttribute("quickFilter", quickFilter);
        
        return "discover";
    }

    // (Hàm buildDiscoverUrl giữ nguyên)
    private String buildDiscoverUrl(int page, String genres, String quickFilter, String today, String threeMonthsAgo) {
        StringBuilder url = new StringBuilder();
        url.append(BASE_URL).append("/discover/movie");
        url.append("?api_key=").append(API_KEY);
        url.append("&language=vi-VN");
        url.append("&page=").append(page); 
        url.append("&include_adult=false");
        
        if (genres != null && !genres.isEmpty()) {
            url.append("&with_genres=").append(genres);
        }
        
        String sortBy = "popularity.desc"; 
        if (quickFilter != null && !quickFilter.isEmpty()) {
            switch (quickFilter) {
                case "trending":
                    sortBy = "popularity.desc";
                    break;
                case "new":
                    sortBy = "primary_release_date.desc";
                    url.append("&primary_release_date.lte=").append(today);
                    url.append("&primary_release_date.gte=").append(threeMonthsAgo);
                    url.append("&vote_count.gte=10"); 
                    break;
                case "top-rated":
                    sortBy = "vote_average.desc";
                    url.append("&vote_count.gte=200"); 
                    break;
            }
        }
        url.append("&sort_by=").append(sortBy);
        
        return url.toString();
    }
    
    // (Hàm getPageTitle giữ nguyên)
    private String getPageTitle(String genres, String quickFilter) {
        if (quickFilter != null) {
            if (quickFilter.equals("trending")) return "Phim Hot Nhất";
            if (quickFilter.equals("new")) return "Phim Mới Ra Mắt";
            if (quickFilter.equals("top-rated")) return "Phim Đánh Giá Cao";
        }
        if (genres != null) {
            switch (genres) {
                case "16": return "Phim Anime";
                case "10751": return "Phim Cho Trẻ Em";
                case "28": return "Phim Hành Động";
                case "12": return "Phim Phiêu Lưu";
                case "35": return "Phim Hài";
                case "80": return "Phim Hình Sự";
                case "18": return "Phim Chính Kịch";
                case "14": return "Phim Giả Tưởng";
                case "27": return "Phim Kinh Dị";
                case "10749": return "Phim Lãng Mạn";
                case "878": return "Phim Khoa Học Viễn Tưởng";
                case "53": return "Phim Gây Cấn";
                default: return "Phim Theo Thể Loại"; 
            }
        }
        return "Khám Phá Phim";
    }

    // (Hàm setEmptyResults giữ nguyên)
    private void setEmptyResults(Model model, String genres, String quickFilter) {
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("pageTitle", getPageTitle(genres, quickFilter));
        model.addAttribute("totalResults", 0);
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 1);
        model.addAttribute("hasResults", false);
        model.addAttribute("genres", genres);
        model.addAttribute("quickFilter", quickFilter);
    }
}