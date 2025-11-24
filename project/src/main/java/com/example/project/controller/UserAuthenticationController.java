// src/main/java/com/example/project/controller/UserAuthenticationController.java
package com.example.project.controller;

import com.example.project.dto.UserLoginDto;
import com.example.project.dto.UserProfileUpdateDto;
import com.example.project.dto.UserRegisterDto;
import com.example.project.dto.UserSessionDto; // <-- IMPORT QUAN TRỌNG
import com.example.project.model.PasswordResetToken;
import com.example.project.model.User;
import com.example.project.service.UserService;
import com.example.project.service.SubscriptionService;
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
    private final SubscriptionService subscriptionService;

    // Giữ lại các mapping @GetMapping cho admin, moderator nếu bạn có
    // (Trong file bạn gửi không có, nhưng file zip có)
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
        // Nhận flash message (thông báo đổi mật khẩu thành công)
        if (model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", model.getAttribute("successMessage"));
        }
        model.addAttribute("user", new UserLoginDto());
        return "Authentication/login";
    }

    /**
     * ĐÃ SỬA LỖI:
     * Sử dụng UserSessionDto thay vì User Entity.
     */
    // src/main/java/com/example/project/controller/UserAuthenticationController.java

    @PostMapping("/login")
    public String login(
            @ModelAttribute("user") UserLoginDto dto,
            Model model,
            HttpSession session) {
        try {
            // 1. Logic kiểm tra đăng nhập (giữ nguyên)
            User user = userService.findByEmail(dto.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại"));

            if (!userService.isPasswordValid(dto.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Sai mật khẩu");
            }

            if (!user.isStatus()) {
                throw new IllegalArgumentException("Tài khoản đã bị khóa");
            }

            // 2. TẠO DTO & XÁC ĐỊNH VAI TRÒ
            UserSessionDto userSession = new UserSessionDto(
                    user.getUserID(),
                    user.getUserName(),
                    user.getEmail(),
                    user.getRole());

            String userRole = user.getRole() != null ? user.getRole().toLowerCase() : "";

            // --- DỌN DẸP SESSION CŨ ---
            session.removeAttribute("user");
            session.removeAttribute("admin");
            session.removeAttribute("contentManager");
            session.removeAttribute("moderator");

            // --- BƯỚC 3: CHUYỂN HƯỚNG ƯU TIÊN (PREV_URL) ---
            String redirectUrl = (String) session.getAttribute("PREV_URL");
            session.removeAttribute("PREV_URL"); // Dọn dẹp ngay lập tức

            // Vẫn phải set session user trước khi redirect
            if ("user".equals(userRole))
                session.setAttribute("user", userSession);
            else if ("admin".equals(userRole))
                session.setAttribute("admin", userSession);
            else if ("content_manager".equals(userRole) || "contentmanager".equals(userRole))
                session.setAttribute("contentManager", userSession);
            else if ("moderator".equals(userRole))
                session.setAttribute("moderator", userSession);

            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                return "redirect:" + redirectUrl; // Chuyển hướng đến trang đã lưu
            }
            // --- KẾT THÚC CHUYỂN HƯỚNG ƯU TIÊN ---

            // 4. CHUYỂN HƯỚNG MẶC ĐỊNH THEO VAI TRÒ (Logic cũ)
            if ("user".equals(userRole)) {
                return "redirect:/";
            } else if ("admin".equals(userRole)) {
                return "redirect:/AdminScreen/homeAdminManager";
            } else if ("content_manager".equals(userRole) || "contentmanager".equals(userRole)) {
                return "redirect:/ContentManagerScreen/homeContentManager";
            } else if ("moderator".equals(userRole)) {
                return "redirect:/ModeratorScreen/homeModeratorManage";
            } else {
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + userRole);
            }

        } catch (IllegalArgumentException e) {
            model.addAttribute("loginError", e.getMessage());
            return "Authentication/login";
        }
    }

    /**
     * ĐÃ SỬA LỖI:
     * Hủy toàn bộ session và chuyển về /login.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("user");
        session.removeAttribute("admin");
        session.removeAttribute("contentManager");
        session.removeAttribute("moderator");
        session.invalidate(); // Hủy session hoàn toàn
        return "redirect:/login"; // Chuyển về trang login
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
            return ResponseEntity.badRequest().body(Map.of("error", errorMsg));
        }

        try {
            userService.createUser(dto);
            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- 1. Trang nhập email để gửi link ---
    @GetMapping("/auth/forgot-password")
    public String showForgotPasswordForm(Model model) {
        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        }
        return "Authentication/forgot-password-email";
    }

    // --- 2. Xử lý gửi email ---
    @PostMapping("/auth/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOptional = userService.findByEmail(email);

        if (userOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Email của bạn chưa đăng kí tài khoản. Vui lòng đăng kí.");
            return "redirect:/register";
        }

        userService.processForgotPassword(email);

        redirectAttributes.addFlashAttribute("message",
                "Đã gửi liên kết đặt lại mật khẩu đến email " + email + ". Vui lòng kiểm tra hộp thư của bạn.");

        return "redirect:/auth/forgot-password";
    }

    // --- 3. Trang nhập mật khẩu mới (sau khi click link) ---
    @GetMapping("/auth/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token,
            Model model,
            RedirectAttributes redirectAttributes) {

        Optional<PasswordResetToken> tokenOptional = userService.validatePasswordResetToken(token);

        if (tokenOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            return "redirect:/login";
        }

        model.addAttribute("token", token);
        if (model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", model.getAttribute("errorMessage"));
        }
        return "Authentication/reset-password-form";
    }

    // --- 4. Xử lý cập nhật mật khẩu mới ---
    @PostMapping("/auth/reset-password")
    public String resetPassword(@RequestParam("token") String token,
            @RequestParam("password") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes) {

        if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Mật khẩu mới không được để trống và phải khớp với xác nhận mật khẩu.");
            return "redirect:/auth/reset-password?token=" + token;
        }

        Optional<PasswordResetToken> tokenOptional = userService.validatePasswordResetToken(token);
        if (tokenOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            return "redirect:/login";
        }

        userService.resetPassword(tokenOptional.get(), newPassword);

        // Chuyển về /login để hiển thị thông báo
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật mật khẩu mới thành công! Vui lòng đăng nhập.");
        return "redirect:/login";
    }

    // --- 6. Hiển thị trang Profile ---
    /**
     * ĐÃ SỬA LỖI:
     * Lấy UserSessionDto từ session và dùng nó để lấy User entity mới.
     */
    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {

        // 1. LẤY DTO TỪ SESSION
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        if (userSession == null) {
            return "redirect:/login";
        }

        // 2. LẤY ENTITY MỚI TỪ DB
        User currentUser = userService.getUserById(userSession.getId());

        // 3. Kiểm tra trạng thái VIP (Premium)
        boolean isVip = subscriptionService.checkActiveSubscription(currentUser.getUserID()); // <--- 3. Kiểm tra VIP
        model.addAttribute("isVip", isVip); // <--- 4. Truyền vào Model

        // 4. Tạo DTO cho form
        UserProfileUpdateDto updateDto = new UserProfileUpdateDto(
                currentUser.getUserID(),
                currentUser.getUserName(),
                currentUser.getEmail(),
                currentUser.getPhoneNumber());

        model.addAttribute("userProfile", updateDto);

        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        }
        if (model.containsAttribute("error")) {
            model.addAttribute("error", model.getAttribute("error"));
        }

        return "User/profile";
    }

    // --- 7. Xử lý Chỉnh sửa Profile ---
    /**
     * ĐÃ SỬA LỖI:
     * Cập nhật lại UserSessionDto trong session nếu thành công.
     */
    @PostMapping("/profile/update")
    public String updateProfile(@Valid @ModelAttribute("userProfile") UserProfileUpdateDto dto,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Kiểm tra xem session có hợp lệ không
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        if (userSession == null || userSession.getId() != dto.getId()) {
            return "redirect:/login"; // Lỗi bảo mật hoặc session hết hạn
        }

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage)
                    .findFirst()
                    .orElse("Dữ liệu nhập vào không hợp lệ.");
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/profile";
        }

        try {
            boolean emailChanged = userService.updateProfile(dto);

            if (emailChanged) {
                session.invalidate();
                redirectAttributes.addFlashAttribute("message",
                        "Email của bạn đã được cập nhật thành công! Vui lòng đăng nhập lại bằng Email mới.");

                return "redirect:/profile/update-success";
            } else {
                // CẬP NHẬT LẠI DTO TRONG SESSION
                User updatedUser = userService.getUserById(dto.getId());
                UserSessionDto updatedSessionDto = new UserSessionDto(
                        updatedUser.getUserID(),
                        updatedUser.getUserName(),
                        updatedUser.getEmail(),
                        updatedUser.getRole());
                session.setAttribute("user", updatedSessionDto); // <-- CẬP NHẬT DTO

                redirectAttributes.addFlashAttribute("message", "Cập nhật hồ sơ thành công!");
                return "redirect:/profile";
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        }
    }

    // --- 8. Trang Thông báo Cập nhật thành công (khi đổi Email) ---
    @GetMapping("/profile/update-success")
    public String updateSuccess(Model model, HttpSession session) {
        if (model.containsAttribute("message")) {
            model.addAttribute("message", model.getAttribute("message"));
        } else {
            return "redirect:/login";
        }

        return "User/update-success";
    }
}