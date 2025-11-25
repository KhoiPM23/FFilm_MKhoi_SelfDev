package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.ProductionCompany;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ProductionCompanyDetailController {

    // [ĐÃ XÓA] API_KEY, BASE_URL và RestTemplate vì không dùng nữa

    @Autowired
    private MovieService movieService;

    @GetMapping({"/company/detail/{id}", "/company/detail"})
    public String companyDetail(
            @PathVariable(required = false, name = "id") String id,      // DB PK
            @RequestParam(required = false, name = "id") String idQuery, // TMDB ID
            Model model
    ) {
        String finalIdStr = (id != null && !id.isEmpty()) ? id : idQuery;
        if (finalIdStr == null || finalIdStr.isEmpty()) return "redirect:/";

        ProductionCompany company = null;

        try {
            int numericId = Integer.parseInt(finalIdStr);

            // 1. Lấy thông tin Company từ DB
            // Hàm này trong Service đã có logic check DB, nếu có trả về Entity (kèm relationship movies)
            if (id != null && !id.isEmpty()) {
                company = movieService.getCompanyByIdOrSync(numericId); // PK
            } else {
                company = movieService.getCompanyOrSync(numericId); // TMDB ID
            }

            if (company == null) {
                return "redirect:/";
            }

            // 2. [SỬA ĐỔI QUAN TRỌNG] Lấy phim trực tiếp từ DB (Relationship)
            // Không gọi API discover nữa -> Chặn đứng việc lazy load từ TMDB
            List<Map<String, Object>> moviesMapList = new ArrayList<>();
            
            Set<Movie> dbMovies = company.getMovies(); // Lấy list phim từ bảng liên kết
            if (dbMovies != null && !dbMovies.isEmpty()) {
                for (Movie movie : dbMovies) {
                    // Convert sang Map để hiển thị (dùng hàm có sẵn của Service)
                    moviesMapList.add(movieService.convertToMap(movie));
                }
            }

            // 3. Sắp xếp phim (Mới nhất lên đầu) - Vì lấy từ Set DB ra thứ tự có thể lộn xộn
            moviesMapList.sort((m1, m2) -> {
                String date1 = (String) m1.getOrDefault("releaseDate", "0000-00-00");
                String date2 = (String) m2.getOrDefault("releaseDate", "0000-00-00");
                // Handle null an toàn
                if (date1 == null) date1 = "0000-00-00";
                if (date2 == null) date2 = "0000-00-00";
                return date2.compareTo(date1); // Giảm dần
            });

            // 4. Gán dữ liệu vào Model
            Map<String, Object> companyData = new HashMap<>();
            companyData.put("id", company.getId());
            companyData.put("name", company.getName());
            companyData.put("originCountry", company.getOriginCountry());
            
            // Logo: Xử lý link ảnh
            String logo = company.getLogoPath();
            if (logo != null && !logo.isEmpty()) {
                // Nếu link đã là full url thì giữ nguyên, nếu không thì thêm prefix
                companyData.put("logo", logo.startsWith("http") ? logo : "https://image.tmdb.org/t/p/w500" + logo);
            } else {
                companyData.put("logo", "/images/placeholder.jpg"); // Fallback logo
            }

            model.addAttribute("company", companyData);
            model.addAttribute("movies", moviesMapList); // List này giờ chứa TOÀN BỘ phim trong DB của hãng
            
            return "company/productionCompanyDetail";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/";
        }
    }
}