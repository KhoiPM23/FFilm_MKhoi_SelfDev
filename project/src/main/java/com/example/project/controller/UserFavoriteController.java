package com.example.project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.project.service.UserFavoriteService;

@Controller
public class UserFavoriteController {
    @Autowired
    private UserFavoriteService favoriteService;

    @GetMapping("path")
    public String getMethodName(@RequestParam String param) {
        return new String();
    }

}
