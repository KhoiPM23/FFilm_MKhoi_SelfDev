package com.example.project.controller;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.example.project.service.UserFavoriteService;
import com.example.project.dto.MovieFavorite;
import com.example.project.dto.UserSessionDto;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.project.dto.AddUserFavoriteRequest;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.UserFavorite;

import jakarta.websocket.Session;

@Controller
@RequestMapping("/favorites")
public class UserFavoriteController {

    @Autowired
    private UserFavoriteService favoriteService;

    @GetMapping("/my-list")
    public String showAllFavorite(
            // 2. Lấy User trực tiếp từ session
            @SessionAttribute("user") UserSessionDto userSession,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

    @PostMapping("/{movieId}")
    public String addFavorite(
            @PathVariable Integer movieId,
            @SessionAttribute("user") User user) {
        Integer userId = user.getUserID();
        AddUserFavoriteRequest req = new AddUserFavoriteRequest(movieId, userId,
                java.sql.Date.valueOf(LocalDate.now()));
        boolean success = favoriteService.addFavorite(req);
        if (success) {
            return "redirect:/movie/detail/" + movieId;
        } else {
            return "error";
        }
    }

}
