package com.example.project.controller;

import com.example.project.dto.UserSessionDto;
import com.example.project.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MessengerController {

    @Autowired private UserService userService; // Giả sử đã có service này

   @GetMapping("/messenger")
    public String messengerPage(Model model, HttpSession session) {
        UserSessionDto user = (UserSessionDto) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        
        model.addAttribute("user", user); // ✅ ĐÃ CÓ
        
        // ✅ THÊM: Convert sang JSON để JS đọc được
        try {
            ObjectMapper mapper = new ObjectMapper();
            String userJson = mapper.writeValueAsString(user);
            model.addAttribute("userJson", userJson);
        } catch (Exception e) {
            model.addAttribute("userJson", "{}");
        }
        
        model.addAttribute("friends", userService.getAllUsers()); 
        return "User/messenger";
    }
}