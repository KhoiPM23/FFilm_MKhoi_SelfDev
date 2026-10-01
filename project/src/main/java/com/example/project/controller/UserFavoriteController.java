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
import jakarta.transaction.Transactional; 
@Controller
@RequestMapping("/favorites")
public class UserFavoriteController {

    @Autowired
    private UserFavoriteService favoriteService;


    private UserSessionDto resolveSessionUser(jakarta.servlet.http.HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("user");
        if (u == null) u = session.getAttribute("admin");
        if (u == null) u = session.getAttribute("moderator");
        if (u == null) u = session.getAttribute("contentManager");

        if (u instanceof UserSessionDto) {
            return (UserSessionDto) u;
        }
        if (u instanceof com.example.project.model.User) {
            com.example.project.model.User userEntity = (com.example.project.model.User) u;
            return new UserSessionDto(userEntity.getUserID(), userEntity.getUserName(), userEntity.getEmail(), userEntity.getRole());
        }
        return null;
    }

    @GetMapping("/my-list")
    public String showAllFavorite(
            jakarta.servlet.http.HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        UserSessionDto userSession = resolveSessionUser(session);
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
        model.addAttribute("size", size);
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
            jakarta.servlet.http.HttpSession session) {

        Map<String, String> response = new HashMap<>();
        UserSessionDto userSession = resolveSessionUser(session);

        if (userSession == null) {
            // Trường hợp chưa đăng nhập
            response.put("status", "unauthorized");
            response.put("message", "Vui lòng đăng nhập để thêm phim yêu thích.");
            return ResponseEntity.status(401).body(response);
        }

        Integer userId = userSession.getId();

        // Delegate to Service
        boolean added = favoriteService.toggleFavorite(userId, movieId);

        if (!added) {
            response.put("status", "removed");
            response.put("message", "Đã xóa khỏi danh sách yêu thích.");
        } else {
            response.put("status", "added");
            response.put("message", "Đã thêm vào danh sách yêu thích.");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<List<Integer>> getFavoriteMovieIds(
            jakarta.servlet.http.HttpSession session) {

        UserSessionDto userSession = resolveSessionUser(session);
        if (userSession == null) {
            return ResponseEntity.status(401).body(java.util.Collections.emptyList());
        }

        Integer userId = userSession.getId();

        List<Integer> favoriteMovieIds = favoriteService.getFavoriteMovieIds(userId);

        return ResponseEntity.ok(favoriteMovieIds);
    }

    @GetMapping("/api/check/{movieId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkFavorite(
            @PathVariable Integer movieId,
            jakarta.servlet.http.HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        UserSessionDto userSession = resolveSessionUser(session);
        if (userSession == null) {
            response.put("isFavorite", false);
            return ResponseEntity.ok(response);
        }
        boolean isFav = favoriteService.isFavorite(userSession.getId(), movieId);
        response.put("isFavorite", isFav);
        return ResponseEntity.ok(response);
    }
}