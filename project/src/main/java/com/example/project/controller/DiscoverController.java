package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // <-- THÊM
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DiscoverController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";
    private static final int PAGE_SIZE = 10; // Giới hạn 10 phim/trang (cho carousel/grid)

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate;

    @GetMapping("/discover")
    public String discover(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres, // Đây là tmdbGenreId
            @RequestParam(required = false) String quickFilter,
            Model model) {

        try {
            // Lấy thông tin phân trang (tổng số) từ API
            // (Chúng ta chấp nhận rằng tổng số này chỉ là của API)
            String page1ApiUrl = buildDiscoverUrl(1, genres, quickFilter);
            int totalResults = 0;
            int totalPages = 1;
            
            try {
                String page1Response = restTemplate.getForObject(page1ApiUrl, String.class);
                if (page1Response != null) {
                    JSONObject page1Json = new JSONObject(page1Response);
                    totalResults = page1Json.optInt("total_results", 0);
                    totalPages = page1Json.optInt("total_pages", 1);
                }
            } catch (Exception e) {
                 System.err.println("Lỗi lấy totalPages/Results: " + e.getMessage());
            }

            // [GIẢI PHÁP 3] Lấy danh sách phim đã gộp
            
            Page<Movie> dbMovies;
            int dbPage = page - 1; // Page của DB (bắt đầu từ 0)

            // 1. Lấy phim từ DB (dựa trên filter)
            if (genres != null && !genres.isEmpty()) {
                dbMovies = movieService.getMoviesByGenreFromDB(Integer.parseInt(genres), PAGE_SIZE, dbPage);
            } else if ("new".equals(quickFilter)) {
                dbMovies = movieService.getNewMoviesFromDB(PAGE_SIZE); // Chỉ lấy 1 trang DB
            } else {
                // "trending" hoặc "top-rated"
                dbMovies = movieService.getHotMoviesFromDB(PAGE_SIZE); // Chỉ lấy 1 trang DB
            }
            
            // 2. Lấy URL API cho trang hiện tại
            String currentApiUrl = buildDiscoverUrl(page, genres, quickFilter);
            
            // 3. Gộp
            List<Map<String, Object>> mergedMovies = movieService.getMergedCarouselMovies(
                currentApiUrl, 
                dbMovies, 
                PAGE_SIZE
            );

            // 4. Set Banner (Lấy phim đầu tiên trong danh sách đã gộp)
            if (!mergedMovies.isEmpty()) {
                Map<String, Object> bannerMap = mergedMovies.get(0);
                int movieID = (int) bannerMap.get("id"); // Lấy PK
                
                String trailerKey = movieService.findBestTrailerKey(movieID); // Gọi bằng PK
                String logoPath = movieService.findBestLogoPath(movieID); // Gọi bằng PK
                
                bannerMap.put("trailerKey", trailerKey);
                bannerMap.put("logoPath", logoPath);
                
                model.addAttribute("banner", bannerMap);
                
                // Top movies là 10 phim đầu tiên của danh sách gộp
                model.addAttribute("topMovies", mergedMovies.subList(0, Math.min(mergedMovies.size(), 10)));
            } else {
                setEmptyResults(model, genres, quickFilter);
            }
            
            // Gán model cho grid (searchResults giống hệt topMovies vì logic gộp chung)
            model.addAttribute("searchResults", mergedMovies);
            model.addAttribute("totalResults", totalResults);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("currentPage", page);
            model.addAttribute("hasResults", !mergedMovies.isEmpty());
            model.addAttribute("genres", genres);
            model.addAttribute("quickFilter", quickFilter);
            model.addAttribute("pageTitle", getPageTitle(genres, quickFilter));

        } catch (Exception e) {
            e.printStackTrace();
            setEmptyResults(model, genres, quickFilter);
        }
        return "discover";
    }
    
    // (Các hàm buildDiscoverUrl, getPageTitle, setEmptyResults giữ nguyên)
    
    private String buildDiscoverUrl(int page, String genres, String quickFilter) {
        StringBuilder url = new StringBuilder();
        url.append(BASE_URL).append("/discover/movie?api_key=").append(API_KEY)
           .append("&language=vi-VN&page=").append(page);
           
        if (genres != null && !genres.isEmpty()) url.append("&with_genres=").append(genres);
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String threeMonthsAgo = LocalDate.now().minusMonths(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String sortBy = "popularity.desc";
        
        if (quickFilter != null) {
            switch (quickFilter) {
                case "trending": 
                    sortBy = "popularity.desc"; 
                    break;
                case "new": 
                    sortBy = "primary_release_date.desc";
                    url.append("&primary_release_date.lte=").append(today)
                       .append("&primary_release_date.gte=").append(threeMonthsAgo)
                       .append("&vote_count.gte=10");
                    break;
                case "top-rated": 
                    sortBy = "vote_average.desc";
                    url.append("&vote_count.gte=200");
                    break;
            }
        }
        url.append("&sort_by=").append(sortBy);
        
        // [FIX VĐ 6] Thêm &include_adult=false
        url.append("&include_adult=false");
        return url.toString();
    }

    private String getPageTitle(String genres, String quickFilter) {
        if ("trending".equals(quickFilter)) return "Phim Hot Nhất";
        if ("new".equals(quickFilter)) return "Phim Mới Ra Mắt";
        if ("top-rated".equals(quickFilter)) return "Phim Đánh Giá Cao";
        if (genres != null) {
            switch (genres) {
                case "28": return "Phim Hành Động";
                case "12": return "Phim Phiêu Lưu";
                case "16": return "Phim Hoạt Hình";
                case "35": return "Phim Hài";
                case "80": return "Phim Hình Sự";
                case "99": return "Phim Tài Liệu";
                case "18": return "Phim Chính Kịch";
                case "10751": return "Phim Gia Đình";
                case "14": return "Phim Giả Tưởng";
                case "36": return "Phim Lịch Sử";
                case "27": return "Phim Kinh Dị";
                case "10402": return "Phim Âm Nhạc";
                case "9648": return "Phim Bí Ẩn";
                case "10749": return "Phim Lãng Mạn";
                case "878": return "Phim Khoa Học Viễn Tưởng";
                case "10770": return "Phim Truyền Hình";
                case "53": return "Phim Gây Cấn";
                case "10752": return "Phim Chiến Tranh";
                case "37": return "Phim Miền Tây";
            }
        }
        return "Khám Phá Phim";
    }

    private void setEmptyResults(Model model, String genres, String quickFilter) {
        model.addAttribute("banner", null);
        model.addAttribute("topMovies", new ArrayList<>());
        model.addAttribute("searchResults", new ArrayList<>());
        model.addAttribute("pageTitle", "Khám Phá Phim");
        model.addAttribute("totalResults", 0);
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 1);
        model.addAttribute("hasResults", false);
        model.addAttribute("genres", genres);
        model.addAttribute("quickFilter", quickFilter);
    }
}