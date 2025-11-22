package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class ModeratorPageController {

    @GetMapping("/moderator/chat")
    public String showChatPage(HttpSession session) {
        if (session.getAttribute("moderator") == null) {
            return "redirect:/login";
        }
        return "ModeratorScreen/moderator-chat"; 
    }
}