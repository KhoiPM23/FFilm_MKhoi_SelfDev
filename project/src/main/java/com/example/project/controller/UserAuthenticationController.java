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

        @GetMapping("/ModeratorScreen/homeModeratorManage")
    public String showModeratorHome() {
        return "ModeratorScreen/homeModeratorManage"; 
    }

            @GetMapping("/AdminScreen/homeAdminManager")
    public String showAdminManager() {
        return "AdminScreen/homeAdminManager"; 
    }

            @GetMapping("/ContentManagerScreen/homeContentManager")
    public String showContentManagement() {
        return "ContentManagerScreen/homeContentManager"; 
    }

        @GetMapping("/register")
        public String showRegisterForm() {
            return "Authentication/register"; 
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
        User user = userService.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại"));

        if (!userService.isPasswordValid(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu không đúng");
        }
        
        String userRole = user.getRole() != null ? user.getRole().toLowerCase() : "";

        if (!user.isStatus()) {
            throw new IllegalArgumentException("Tài khoản chưa được kích hoạt.");
        }

        if ("user".equals(userRole)) { 
            session.setAttribute("user", user); 
            return "redirect:/";
        } else if ("admin".equals(userRole)) { 
            session.setAttribute("admin", user);
            return "redirect:/AdminScreen/homeAdminManager";
        } else if ("content_manager".equals(userRole) || "contentmanager".equals(userRole)) { 
            session.setAttribute("contentManager", user); 
            return "redirect:ContentManagerScreen/homeContentManager";
        } else if ("moderator".equals(userRole)) { 
            session.setAttribute("moderator", user); 
            return "redirect:/ModeratorScreen/homeModeratorManage"; 
        } else {
            throw new IllegalArgumentException("Tài khoản không có quyền truy cập hoặc vai trò không hợp lệ.");
        }

    } catch (IllegalArgumentException e) {
        model.addAttribute("loginError", e.getMessage());
        return "Authentication/login"; 
    }
}

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
            userService.createUser(dto); 
            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("user"); 
        session.removeAttribute("admin"); 
        session.removeAttribute("contentManager"); 
        session.removeAttribute("moderator"); 
        
        return "redirect:/"; // Trả về trang homepage
    }
    }