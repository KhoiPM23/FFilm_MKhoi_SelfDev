package com.example.project.controller;

import com.example.project.dto.UserLoginDto;
import com.example.project.dto.UserRegisterDto;
import com.example.project.dto.UserDto;
import com.example.project.model.User;
import com.example.project.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class UserAuthenticationController {

    private final UserService userService;

    // ==================== MVC: FORM ĐĂNG KÝ / ĐĂNG NHẬP ====================

    @GetMapping("/register")
    public String showRegisterForm() {
        return "Authentication/register"; // HTML có JS gọi API
    }

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("user", new UserLoginDto());
        return "Authentication/login";
    }

    @PostMapping("/login")
    public String login(
            @ModelAttribute("user") UserLoginDto dto,
            Model model,
            HttpSession session) {
        try {
            // GỌI SERVICE CHỈ ĐỂ LẤY USER
            User user = userService.findByEmail(dto.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại"));

            // KIỂM TRA MẬT KHẨU Ở CONTROLLER
            if (!userService.isPasswordValid(dto.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Mật khẩu không đúng");
            }

            session.setAttribute("user", user);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "Authentication/login";
        }
    }

    // ==================== REST API: ĐĂNG KÝ (AJAX) ====================

@PostMapping("/api/register")
@ResponseBody
public ResponseEntity<?> registerApi(@Valid @RequestBody UserRegisterDto dto,
                                     BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        String errorMsg = bindingResult.getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Dữ liệu không hợp lệ");
        return ResponseEntity.badRequest().body(errorMsg);
    }

    try {
        userService.createUser(dto); // → TỰ ĐỘNG HASH + ROLE + STATUS
        return ResponseEntity.ok(Map.of("message", "Đăng ký thành công!"));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
}