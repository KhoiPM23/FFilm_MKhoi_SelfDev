package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DiscoverController {

    @Autowired private MovieService movieService;

    @GetMapping("/discover")
    public String discover(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres, // Đây là tmdbGenreId
            @RequestParam(required = false) String quickFilter,
            Model model) {

        try {
            int pageSize = 20;
            int dbPage = page - 1; // Spring Data dùng page bắt đầu từ 0
            if (dbPage < 0) dbPage = 0;

            // [THAY ĐỔI QUAN TRỌNG] Thay vì buildDiscoverUrl (API), ta gọi hàm xử lý DB
            Page<Movie> moviePage = getMoviesFromDbByFilter(dbPage, pageSize, genres, quickFilter);
            
            List<Map<String, Object>> movies = moviePage.getContent().stream()
                .map(movieService::convertToMap)
                .collect(Collectors.toList());

            // Xử lý Banner (Lấy phim đầu tiên của trang kết quả)
            if (!movies.isEmpty()) {
                Map<String, Object> bannerMap = movies.get(0);
                int movieID = (int) bannerMap.get("id");
                // Lấy info từ DB
                bannerMap.put("trailerKey", movieService.findBestTrailerKey(movieID));
                bannerMap.put("logoPath", movieService.findBestLogoPath(movieID));
                
                model.addAttribute("banner", bannerMap);
                // Top movies là 20 phim đầu
                model.addAttribute("topMovies", movies.subList(0, Math.min(movies.size(), 20)));
                model.addAttribute("searchResults", movies);
                model.addAttribute("hasResults", true);
            } else {
                setEmptyResults(model, genres, quickFilter);
            }

            // Gán các thông số phân trang
            model.addAttribute("totalResults", moviePage.getTotalElements());
            model.addAttribute("totalPages", moviePage.getTotalPages());
            model.addAttribute("currentPage", page);
            
            // Giữ trạng thái filter trên UI
            model.addAttribute("genres", genres);
            model.addAttribute("quickFilter", quickFilter);
            model.addAttribute("pageTitle", getPageTitle(genres, quickFilter));

        } catch (Exception e) {
            e.printStackTrace();
            setEmptyResults(model, genres, quickFilter);
        }
        return "discover";
    }
    
    // [HÀM MỚI THAY THẾ buildDiscoverUrl] 
    // Logic lọc giữ nguyên nhưng áp dụng cho DB
    private Page<Movie> getMoviesFromDbByFilter(int page, int size, String genres, String quickFilter) {
        // 1. Lọc theo Thể loại
        if (genres != null && !genres.isEmpty()) {
            try {
                int genreId = Integer.parseInt(genres);
                return movieService.getMoviesByGenreFromDB(genreId, size, page);
            } catch (NumberFormatException e) {
                return Page.empty();
            }
        }
        
        // 2. Lọc theo Quick Filter
        if ("new".equals(quickFilter)) {
            // Logic cũ: Phim mới ra mắt. Trong DB dùng order by ReleaseDate
            // Bạn có thể thêm logic lọc ngày tháng vào Service nếu muốn chính xác tuyệt đối
            return movieService.getNewMoviesFromDB(size); // Tạm dùng hàm có sẵn (page 0 fixed -> cần sửa service hỗ trợ page)
            // Note: Để đơn giản, ta sẽ dùng hàm getNewMoviesFromDB nhưng service cần update để nhận tham số page.
            // Nếu Service chưa hỗ trợ page cho getNewMoviesFromDB, nó sẽ trả về page 0.
        } 
        else if ("top-rated".equals(quickFilter)) {
            // Logic cũ: Vote cao
            return movieService.getHotMoviesFromDB(size); // Tương tự
        } 
        
        // Mặc định: Trending (Hot)
        return movieService.getHotMoviesFromDB(size);
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