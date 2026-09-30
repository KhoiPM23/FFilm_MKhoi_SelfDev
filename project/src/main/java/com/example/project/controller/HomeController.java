package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    // ---- 1. CẤU HÌNH & REPOSITORY ----
    @Autowired
    private MovieService movieService;

    // ---- 2. MAIN HOME LOGIC ----

    // Hiển thị trang chủ
    @GetMapping("/")
    public String home(Model model) {
        try {
            // [THAY ĐỔI] Chuyển sang chế độ Offline: Chỉ lấy từ DB
            // Lý do: Seeder đã nạp đủ phim, không cần gọi API mỗi lần load trang để tránh
            // độ trễ.

            int carouselLimit = 20;

            // 1. Tải Phim Hot (Trending) từ cache / DB
            List<Map<String, Object>> hotMovies = movieService.getHotMoviesMapList(carouselLimit);
            model.addAttribute("hotMovies", hotMovies);

            // 2. Set Banner (Lấy phim đầu tiên trong list Hot)
            setBanner(model, hotMovies);

            // 3. Tải các carousel còn lại từ cache / DB (tốc độ < 0.27s)
            try {
                model.addAttribute("newMovies", movieService.getNewMoviesMapList(carouselLimit));
            } catch (Exception ex) {
                model.addAttribute("newMovies", new ArrayList<>());
            }

            try {
                model.addAttribute("animeMovies", movieService.getGenreMoviesMapList(16, carouselLimit, 0));
            } catch (Exception ex) {
                model.addAttribute("animeMovies", new ArrayList<>());
            }

            try {
                model.addAttribute("kidsMovies", movieService.getGenreMoviesMapList(10751, carouselLimit, 0));
            } catch (Exception ex) {
                model.addAttribute("kidsMovies", new ArrayList<>());
            }

            try {
                model.addAttribute("actionMovies", movieService.getGenreMoviesMapList(28, carouselLimit, 0));
            } catch (Exception ex) {
                model.addAttribute("actionMovies", new ArrayList<>());
            }

            return "index";

        } catch (Exception e) {
            log.error("Error in home(): {}", e.getMessage(), e);
            ensureDefaultAttributes(model); // Hàm fallback an toàn
            return "index";
        }
    }

    // ---- 3. HELPER FUNCTIONS ----

    // Set Banner từ danh sách phim
    private void setBanner(Model model, List<Map<String, Object>> hotMovies) {
        try {
            if (hotMovies != null && !hotMovies.isEmpty()) {
                Map<String, Object> bannerMap = new HashMap<>(hotMovies.get(0));
                int movieID = (int) bannerMap.get("id"); // Lấy ID khóa chính (PK)

                // [THAY ĐỔI] Gọi Service tìm Trailer/Logo trong DB
                // Hàm này trong Service đã được sửa để return field từ Entity chứ không gọi API
                // nữa
                String trailerKey = movieService.findBestTrailerKey(movieID);
                String logoPath = movieService.findBestLogoPath(movieID);

                bannerMap.put("trailerKey", trailerKey);
                bannerMap.put("logoPath", logoPath);
                model.addAttribute("banner", bannerMap);
                return;
            }
        } catch (Exception e) {
            System.err.println("Error in setBanner: " + e.getMessage());
        }

        // Fallback nếu có lỗi
        model.addAttribute("banner", createDefaultBanner());
    }

    // Tạo Banner mặc định (khi DB trống)
    private Map<String, Object> createDefaultBanner() {
        Map<String, Object> banner = new HashMap<>();
        banner.put("id", 0);
        banner.put("title", "FFilm - Xem phim thả ga");
        banner.put("overview", "Hệ thống đang cập nhật dữ liệu. Vui lòng quay lại sau.");
        banner.put("backdrop", "/images/placeholder.jpg");
        banner.put("rating", "10");
        banner.put("year", "2025");
        banner.put("runtime", 0);
        banner.put("country", "Việt Nam");
        return banner;
    }

    // Đảm bảo model không bị null (Tránh lỗi Thymeleaf)
    private void ensureDefaultAttributes(Model model) {
        if (!model.containsAttribute("banner"))
            model.addAttribute("banner", createDefaultBanner());
        model.addAttribute("hotMovies", new ArrayList<>());
        model.addAttribute("newMovies", new ArrayList<>());
        model.addAttribute("animeMovies", new ArrayList<>());
        model.addAttribute("kidsMovies", new ArrayList<>());
        model.addAttribute("actionMovies", new ArrayList<>());
    }

    @GetMapping("/history")
    public String watchHistoryPage() {
        return "service/watch-history";
    }
}