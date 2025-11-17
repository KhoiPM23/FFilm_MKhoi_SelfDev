package com.example.project.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.dto.MovieFavorite;
import com.example.project.dto.UserSessionDto;
import com.example.project.service.UserFavoriteService;

@Controller
@RequestMapping("/favorites")
public class UserFavoriteController {

    @Autowired 
    private UserFavoriteService favoriteService;

    // Trang hiển thị danh sách yêu thích (Server-side render)
    @GetMapping("/my-list")
    public String showAllFavorite(
            @SessionAttribute(name = "user", required = false) UserSessionDto userSession,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        
        if (userSession == null) return "redirect:/login";
        
        Page<MovieFavorite> moviePage = favoriteService.showFavoriteList(userSession.getId(), page, size);
        
        model.addAttribute("movieFavorites", moviePage.getContent());
        model.addAttribute("currentPage", moviePage.getNumber());
        model.addAttribute("totalPages", moviePage.getTotalPages());
        model.addAttribute("totalItems", moviePage.getTotalElements());
        
        return "service/list-favorite";
    }

    // [FIX] API Toggle Like - Trả về JSON cho JS xử lý
    @PostMapping("/{tmdbId}")
    @ResponseBody 
    public ResponseEntity<?> toggleFavorite(
            @PathVariable Integer tmdbId,
            @SessionAttribute(name = "user", required = false) UserSessionDto user) {
        
        // 1. Kiểm tra đăng nhập
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "unauthorized", "message", "Vui lòng đăng nhập"));
        }

        // 2. Tạo request DTO
        AddUserFavoriteRequest req = new AddUserFavoriteRequest(
            tmdbId, 
            user.getId(), 
            java.sql.Date.valueOf(LocalDate.now())
        );
        
        // 3. Gọi Service xử lý (Thêm hoặc Xóa)
        String result = favoriteService.toggleFavorite(req);
        
        // 4. Trả về kết quả JSON
        if ("error".equals(result)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Không tìm thấy phim hoặc lỗi hệ thống"));
        }
        
        // Trả về: { "status": "added" } hoặc { "status": "removed" }
        return ResponseEntity.ok(Map.of("status", result));
    }
}