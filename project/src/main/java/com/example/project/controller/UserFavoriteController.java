package com.example.project.controller;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map; // <-- THÊM
import java.util.HashMap;

import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // <-- THÊM
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody; // <-- THÊM

import com.example.project.service.UserFavoriteService;
import com.example.project.dto.MovieFavorite;
import com.example.project.dto.UserSessionDto;
import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.repository.FavoriteRepository; // <-- THÊM
import com.example.project.model.UserFavorite;
import com.example.project.model.UserFavoriteId; // <-- THÊM
import jakarta.transaction.Transactional; // <-- THÊM

@Controller
@RequestMapping("/favorites")
public class UserFavoriteController {

    @Autowired
    private UserFavoriteService favoriteService;

    @Autowired
    private FavoriteRepository favoriteRepository; // <-- Cần để thực hiện logic toggle

    @GetMapping("/my-list")
    public String showAllFavorite(
            // 2. Lấy User trực tiếp từ session
            @SessionAttribute("user") UserSessionDto userSession,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // 3. Lấy ID từ đối tượng user đã lấy từ session
        if (userSession == null) {
            return "redirect:/login";
        }
        Integer userId = userSession.getId();
        Page<MovieFavorite> moviePage = favoriteService.showFavoriteList(userId, page, size);
        List<MovieFavorite> movieFavorites = moviePage.getContent();
        model.addAttribute("movieFavorites", movieFavorites);
        model.addAttribute("currentPage", moviePage.getNumber());
        model.addAttribute("totalPages", moviePage.getTotalPages());
        model.addAttribute("totalItems", moviePage.getTotalElements());
        return "service/list-favorite";
    }

    /**
     * [SỬA LỖI] Phương thức mới: Toggle Favorite (Thêm/Xóa) và trả về JSON status.
     */
    @Transactional
    @PostMapping("/{movieId}")
    @ResponseBody // Trả về JSON
    public ResponseEntity<Map<String, String>> toggleFavorite(
            @PathVariable Integer movieId,
            @SessionAttribute(name = "user", required = false) UserSessionDto userSession) {

        Map<String, String> response = new HashMap<>();

        if (userSession == null) {
            // Trường hợp chưa đăng nhập
            response.put("status", "unauthorized");
            response.put("message", "Vui lòng đăng nhập để thêm phim yêu thích.");
            return ResponseEntity.status(401).body(response);
        }

        Integer userId = userSession.getId();

        // 1. Kiểm tra trạng thái hiện tại
        boolean exists = favoriteRepository.existsByUserIDAndMovieID(userId, movieId);

        if (exists) {
            // 2. Nếu đã tồn tại -> XÓA
            favoriteRepository.deleteById(new UserFavoriteId(movieId, userId));
            response.put("status", "removed");
            response.put("message", "Đã xóa khỏi danh sách yêu thích.");
        } else {
            // 3. Nếu chưa tồn tại -> THÊM
            UserFavorite uf = new UserFavorite();
            uf.setUserID(userId);
            uf.setMovieID(movieId);
            uf.setCreateAt(new Date(System.currentTimeMillis()));
            favoriteRepository.save(uf);

            response.put("status", "added");
            response.put("message", "Đã thêm vào danh sách yêu thích.");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<List<Integer>> getFavoriteMovieIds(
            @SessionAttribute(name = "user", required = false) UserSessionDto userSession) {

        if (userSession == null) {
            return ResponseEntity.status(401).build();
        }

        Integer userId = userSession.getId();

        // Lấy tất cả movieId từ bảng UserFavorite (không cần phân trang vì frontend chỉ
        // cần check tồn tại)
        List<Integer> favoriteMovieIds = favoriteRepository.findMovieIdsByUserID(userId);

        return ResponseEntity.ok(favoriteMovieIds);
    }
}