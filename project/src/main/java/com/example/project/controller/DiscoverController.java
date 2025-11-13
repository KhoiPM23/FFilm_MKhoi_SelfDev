package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate; // [G29] Vẫn cần vì hàm buildDiscoverUrl dùng

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DiscoverController {

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    @Autowired private MovieService movieService;
    @Autowired private RestTemplate restTemplate; // [G29] Giữ lại

    @GetMapping("/discover")
    public String discover(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String quickFilter,
            Model model) {

        try {
            // [SỬA] BƯỚC 1: LUÔN LUÔN LẤY BANNER TỪ PAGE 1
            // Xây dựng URL cho page 1 (để lấy banner + top movies)
            String page1Url = buildDiscoverUrl(1, genres, quickFilter);
            int discoverLimit = 20; 
            Map<String, Object> page1Data = movieService.loadAndSyncPaginatedMovies(page1Url, discoverLimit);
            List<Map<String, Object>> topMovies = (List<Map<String, Object>>) page1Data.get("movies");

            // [SỬA] BƯỚC 2: GÁN BANNER VÀ TOP MOVIES (Luôn cố định)
            if (!topMovies.isEmpty()) {
                // Lấy bannerTmdbId từ topMovies (của page 1)
                int bannerTmdbId = (int) topMovies.get(0).get("id");
                // Gọi getMoviePartial để nâng cấp (lấy duration/country)
                Movie bannerMovie = movieService.getMoviePartial(bannerTmdbId); 
                Map<String, Object> bannerMap = movieService.convertToMap(bannerMovie);

                String trailerKey = movieService.findBestTrailerKey(bannerTmdbId);
                String logoPath = movieService.findBestLogoPath(bannerTmdbId);
                
                bannerMap.put("trailerKey", trailerKey);
                bannerMap.put("logoPath", logoPath);
                
                model.addAttribute("banner", bannerMap);
                // topMovies carousel luôn là danh sách của page 1
                model.addAttribute("topMovies", topMovies.subList(0, Math.min(topMovies.size(), 10)));
            } else {
                // Fallback nếu page 1 cũng không có gì
                setEmptyResults(model, genres, quickFilter); // setEmptyResults sẽ tự tạo banner rỗng
            }
            
            // [SỬA] BƯỚC 3: LẤY KẾT QUẢ CHO GRID PHÂN TRANG
            List<Map<String, Object>> gridMovies;
            int totalResults = (int) page1Data.get("totalResults");
            int totalPages = (int) page1Data.get("totalPages");

            if (page == 1) {
                // Tiết kiệm API: Nếu đang ở trang 1, dùng luôn kết quả đã lấy
                gridMovies = topMovies;
            } else {
                // Nếu ở trang > 1, gọi API lần nữa CHỈ để lấy grid
                String currentPageUrl = buildDiscoverUrl(page, genres, quickFilter);
                Map<String, Object> currentPageData = movieService.loadAndSyncPaginatedMovies(currentPageUrl, discoverLimit);
                gridMovies = (List<Map<String, Object>>) currentPageData.get("movies");
                // (totalResults và totalPages đã lấy từ page 1 là đủ)
            }
            
            // Gán model cho grid
            model.addAttribute("searchResults", gridMovies);
            model.addAttribute("totalResults", totalResults);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("currentPage", page);
            model.addAttribute("hasResults", !gridMovies.isEmpty());
            model.addAttribute("genres", genres);
            model.addAttribute("quickFilter", quickFilter);
            model.addAttribute("pageTitle", getPageTitle(genres, quickFilter));

        } catch (Exception e) {
            e.printStackTrace();
            setEmptyResults(model, genres, quickFilter);
        }
        return "discover";
    }

    // (Hàm buildDiscoverUrl, getPageTitle, setEmptyResults giữ nguyên)
    
    private String buildDiscoverUrl(int page, String genres, String quickFilter) {
        StringBuilder url = new StringBuilder();
        url.append(BASE_URL).append("/discover/movie?api_key=").append(API_KEY)
           .append("&language=vi-VN&include_adult=false&page=").append(page);
        if (genres != null && !genres.isEmpty()) url.append("&with_genres=").append(genres);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String threeMonthsAgo = LocalDate.now().minusMonths(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String sortBy = "popularity.desc";
        if (quickFilter != null) {
            switch (quickFilter) {
                case "trending": sortBy = "popularity.desc"; break;
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