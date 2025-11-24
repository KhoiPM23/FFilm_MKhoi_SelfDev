package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PersonDetailController {

    @Autowired
    private MovieService movieService;

    /**
     * Hiển thị chi tiết diễn viên/đạo diễn.
     * CHỈ sử dụng dữ liệu từ Database (Offline Mode).
     */
    @GetMapping("/person/detail/{id}")
    public String personDetail(@PathVariable("id") int personId, Model model) {
        
        // 1. Lấy thông tin Person từ Database thông qua Service
        // (Giả định Service đã được sửa để không gọi API fallback nữa)
        Person person = movieService.getPersonByIdOrSync(personId);

        if (person == null) {
            // Nếu không tìm thấy người này trong DB, quay về trang chủ
            return "redirect:/"; 
        }

        // 2. Lấy danh sách phim đã tham gia từ quan hệ @ManyToMany trong DB
        // Hibernate sẽ tự động query bảng trung gian 'Movie_Person'
        List<Movie> personMovies = person.getMovies();
        
        List<Map<String, Object>> moviesMapList = new ArrayList<>();

        if (personMovies != null) {
            for (Movie movie : personMovies) {
                // Convert Entity Movie sang Map để hiển thị ra View (dùng hàm chung của Service)
                Map<String, Object> movieMap = movieService.convertToMap(movie);
                
                // 3. Xử lý vai trò (Role) dựa trên dữ liệu DB
                // Logic: Nếu tên người này trùng với tên Đạo diễn của phim -> Là Đạo diễn
                // Ngược lại -> Mặc định là Diễn viên (vì DB chưa lưu tên vai diễn cụ thể)
                String role = "Diễn viên";
                if (movie.getDirector() != null && movie.getDirector().equalsIgnoreCase(person.getFullName())) {
                    role = "Đạo diễn";
                }
                movieMap.put("role_info", role);
                
                moviesMapList.add(movieMap);
            }
        }

        // 4. Sắp xếp danh sách phim: Mới nhất lên đầu (theo năm phát hành)
        moviesMapList.sort((m1, m2) -> {
            String date1 = (String) m1.getOrDefault("releaseDate", "0000-00-00");
            String date2 = (String) m2.getOrDefault("releaseDate", "0000-00-00");
            // Sort giảm dần (date2 so với date1)
            return date2.compareTo(date1); 
        });

        // 5. Đẩy dữ liệu sang View (Thymeleaf)
        model.addAttribute("person", movieService.convertToMap(person));
        model.addAttribute("movies", moviesMapList);
        
        // Biến này dùng để báo cho frontend biết data đã load xong từ server
        model.addAttribute("clientSideLoad", false); 

        return "person/person-detail";
    }
}