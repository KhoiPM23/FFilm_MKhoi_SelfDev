package com.example.project.controller;

import com.example.project.dto.UserLoginDto;
import com.example.project.dto.UserProfileUpdateDto;
import com.example.project.dto.UserRegisterDto;
import com.example.project.model.PasswordResetToken; // Import bị trùng, nhưng giữ lại cho rõ ràng
import com.example.project.model.User;
import com.example.project.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

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

            session.setAttribute("user", user);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
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

// --- 1. Trang nhập email để gửi link ---
    @GetMapping("/auth/forgot-password")
    public String showForgotPasswordForm(Model model) {
        // Nhận flash message (thông báo gửi email thành công hoặc chuyển từ /register về)
        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        }
        return "Authentication/forgot-password-email"; 
    }

    // --- 2. Xử lý gửi email ---
    @PostMapping("/auth/forgot-password") // Đã sửa: Thêm prefix /auth/ cho nhất quán
    public String processForgotPassword(@RequestParam("email") String email, 
                                        RedirectAttributes redirectAttributes) {
        
        Optional<User> userOptional = userService.findByEmail(email);

        if (userOptional.isEmpty()) {
            // Mail chưa đăng kí -> Thông báo và chuyển hướng sang trang đăng kí
            redirectAttributes.addFlashAttribute("message", "Email của bạn chưa đăng kí tài khoản. Vui lòng đăng kí.");
            return "redirect:/register"; // Đã sửa: Bỏ /auth/ để khớp với @GetMapping("/register")
        }

        // Mail đã đăng kí -> Gửi link
        userService.processForgotPassword(email);
        
        redirectAttributes.addFlashAttribute("message", 
            "Đã gửi liên kết đặt lại mật khẩu đến email " + email + ". Vui lòng kiểm tra hộp thư của bạn.");
        
        // Chuyển hướng về trang nhập email để hiển thị thông báo thành công
        return "redirect:/auth/forgot-password"; 
    }

    // --- 3. Trang nhập mật khẩu mới (sau khi click link) ---
    @GetMapping("/auth/reset-password") // Đã sửa: Thêm prefix /auth/ cho nhất quán (Link trong email phải trỏ đến đây)
    public String showResetPasswordForm(@RequestParam("token") String token, 
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        
        Optional<PasswordResetToken> tokenOptional = userService.validatePasswordResetToken(token);

        if (tokenOptional.isEmpty()) {
            // Token không hợp lệ hoặc đã hết hạn
            redirectAttributes.addFlashAttribute("errorMessage", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            return "redirect:/login"; // Đã sửa: Bỏ /auth/ để khớp với @GetMapping("/login")
        }

        // Token hợp lệ -> Chuyển đến form nhập mật khẩu
        model.addAttribute("token", token);
        // Nhận flash message (thông báo lỗi validate password)
        if (model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", model.getAttribute("errorMessage"));
        }
        return "Authentication/reset-password-form"; // Template mới
    }

    // --- 4. Xử lý cập nhật mật khẩu mới ---
    @PostMapping("/auth/reset-password") // Đã sửa: Thêm prefix /auth/ cho nhất quán
    public String resetPassword(@RequestParam("token") String token, 
                                @RequestParam("password") String newPassword, 
                                @RequestParam("confirmPassword") String confirmPassword,
                                RedirectAttributes redirectAttributes) {

        if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmPassword)) {
            // Mật khẩu mới và xác nhận mật khẩu không khớp
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu mới không được để trống và phải khớp với xác nhận mật khẩu.");
            return "redirect:/auth/reset-password?token=" + token; // Giữ nguyên để khớp với GET mapping đã sửa
        }
        
        // Validate lại token lần nữa trước khi cập nhật
        Optional<PasswordResetToken> tokenOptional = userService.validatePasswordResetToken(token);
        if (tokenOptional.isEmpty()) {
             redirectAttributes.addFlashAttribute("errorMessage", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            return "redirect:/login"; // Đã sửa: Bỏ /auth/ để khớp với @GetMapping("/login")
        }

        // Reset password và xóa token
        userService.resetPassword(tokenOptional.get(), newPassword);

        // Chuyển hướng đến trang thông báo thành công
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật mật khẩu mới thành công! Bạn sẽ được chuyển hướng về trang đăng nhập sau 10 giây.");
        return "redirect:/auth/reset-password-success"; 
    }
    
    // --- 5. Trang thông báo thành công và chuyển hướng ---
    @GetMapping("/auth/reset-password-success") // Đã sửa: Thêm prefix /auth/ cho nhất quán
    public String resetPasswordSuccess(Model model) {
        // Template này chứa JS để tự động redirect sau 10s
        return "Authentication/reset-password-success"; 
    }
    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        // Kiểm tra đăng nhập
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        // Tạo DTO từ User hiện tại để điền vào form chỉnh sửa
        UserProfileUpdateDto updateDto = new UserProfileUpdateDto(
            user.getUserID(), 
            user.getUserName(), 
            user.getEmail(), 
            user.getPhoneNumber()
        );

        model.addAttribute("userProfile", updateDto);
        
        // Lấy thông báo flash (thành công hoặc lỗi)
        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        }
        if (model.containsAttribute("error")) {
            model.addAttribute("error", model.getAttribute("error"));
        }
        
        return "User/profile"; // Template mới
    }


    // --- 7. Xử lý Chỉnh sửa Profile ---
    @PostMapping("/profile/update")
    public String updateProfile(@Valid @ModelAttribute("userProfile") UserProfileUpdateDto dto,
                                BindingResult bindingResult,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {

        // 1. Validation từ DTO (ví dụ: @Email, @NotBlank)
        if (bindingResult.hasErrors()) {
            // Lấy lỗi đầu tiên và quay lại trang profile
            String errorMsg = bindingResult.getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage)
                    .findFirst()
                    .orElse("Dữ liệu nhập vào không hợp lệ.");
            redirectAttributes.addFlashAttribute("error", errorMsg);
            
            // Lỗi validation không cập nhật session, chỉ redirect về trang profile
            return "redirect:/profile";
        }

        try {
            boolean emailChanged = userService.updateProfile(dto);
            
            if (emailChanged) {
                // Ràng buộc: Email thay đổi -> Thông báo và chuyển hướng đăng nhập lại
                session.invalidate(); // Hủy session cũ
                redirectAttributes.addFlashAttribute("message", 
                    "Email của bạn đã được cập nhật thành công! Vui lòng đăng nhập lại bằng Email mới.");
                
                // Chuyển hướng đến trang thông báo
                return "redirect:/profile/update-success"; 
            } else {
                // Chỉ thay đổi UserName/SĐT -> Cập nhật Session và tiếp tục
                User updatedUser = userService.getUserById(dto.getId());
                session.setAttribute("user", updatedUser);
                redirectAttributes.addFlashAttribute("message", "Cập nhật hồ sơ thành công!");
                return "redirect:/profile";
            }
        } catch (IllegalArgumentException e) {
            // Lỗi nghiệp vụ (Email/SĐT đã tồn tại)
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        }
    }
    
    // --- 8. Trang Thông báo Cập nhật thành công và Chuyển hướng (cho trường hợp đổi Email) ---
    @GetMapping("/profile/update-success")
    public String updateSuccess(Model model, HttpSession session) {
        // Nhận flash message và chuẩn bị cho việc tự động chuyển hướng
        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        } else {
            // Nếu không có message, có thể đây là truy cập trực tiếp
            return "redirect:/login"; 
        }
        
        return "User/update-success"; // Template mới
    }
}