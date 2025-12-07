package com.example.project.controller;

import com.example.project.dto.PublicProfileDto;
import com.example.project.dto.UserSessionDto;
import com.example.project.dto.NotificationDto;
import com.example.project.model.Notification; // Import quan trọng
import com.example.project.service.NotificationService;
import com.example.project.service.SocialService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Import quan trọng
import java.util.Map;

@Controller
@RequestMapping("/social")
public class SocialController {

    @Autowired private SocialService socialService;
    @Autowired private NotificationService notificationService;

    // --- 1. VIEW: PROFILE CÔNG KHAI ---
    @GetMapping("/profile/{userId}")
    public String userProfile(@PathVariable Integer userId, Model model, HttpSession session) {
        UserSessionDto currentUser = (UserSessionDto) session.getAttribute("user");
        Integer viewerId = (currentUser != null) ? currentUser.getId() : null;

        try {
            PublicProfileDto profile = socialService.getUserProfile(viewerId, userId);
            model.addAttribute("profile", profile);
            return "User/public-profile"; 
        } catch (Exception e) {
            return "redirect:/watch-party?error=profile_not_found";
        }
    }

    // --- 2. API: FOLLOW SYSTEM ---
    @PostMapping("/api/follow/{targetId}")
    @ResponseBody
    public ResponseEntity<?> followUser(@PathVariable Integer targetId, HttpSession session) {
        UserSessionDto currentUser = (getUserSession(session));
        if (currentUser == null) return ResponseEntity.status(401).body("Vui lòng đăng nhập");

        try {
            socialService.followUser(currentUser.getId(), targetId);
            return ResponseEntity.ok("Followed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/unfollow/{targetId}")
    @ResponseBody
    public ResponseEntity<?> unfollowUser(@PathVariable Integer targetId, HttpSession session) {
        UserSessionDto currentUser = (getUserSession(session));
        if (currentUser == null) return ResponseEntity.status(401).body("Vui lòng đăng nhập");

        socialService.unfollowUser(currentUser.getId(), targetId);
        return ResponseEntity.ok("Unfollowed");
    }

    // --- 3. API: FRIEND SYSTEM ---
    @PostMapping("/add-friend/{targetId}")
    @ResponseBody
    public ResponseEntity<?> sendFriendRequest(@PathVariable Integer targetId, HttpSession session) {
        UserSessionDto currentUser = (getUserSession(session));
        if (currentUser == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            // Chỉ gọi Service (Service đã lo việc lưu DB và gửi Socket Notification)
            socialService.sendFriendRequest(currentUser.getId(), targetId);
            return ResponseEntity.ok(Map.of("status", "SENT", "message", "Đã gửi lời mời"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/accept-friend/{senderId}")
    @ResponseBody
    public ResponseEntity<?> acceptFriend(@PathVariable Integer senderId, HttpSession session) {
        UserSessionDto currentUser = (getUserSession(session));
        if (currentUser == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            socialService.acceptFriendRequest(currentUser.getId(), senderId);
            return ResponseEntity.ok(Map.of("status", "FRIEND", "message", "Đã kết bạn"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- 4. API: NOTIFICATION SYSTEM (HEADER) ---
    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<List<NotificationDto>> getNotifications(HttpSession session) {
        UserSessionDto currentUser = getUserSession(session); // Dùng hàm helper có sẵn trong file cũ
        if (currentUser == null) return ResponseEntity.status(401).build();
        
        // Gọi hàm mới trả về DTO
        return ResponseEntity.ok(notificationService.getUserNotificationsDto(currentUser.getId()));
    }
    
    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public ResponseEntity<?> markAllRead(HttpSession session) {
        UserSessionDto currentUser = (getUserSession(session));
        if (currentUser != null) {
            notificationService.markAllAsRead(currentUser.getId());
        }
        return ResponseEntity.ok("OK");
    }

    // Helper lấy session an toàn
    private UserSessionDto getUserSession(HttpSession session) {
        return (UserSessionDto) session.getAttribute("user");
    }

    // [NEW] API Hủy kết bạn
    @PostMapping("/unfriend/{targetId}")
    @ResponseBody
    public ResponseEntity<?> unfriendUser(@PathVariable Integer targetId, HttpSession session) {
        UserSessionDto currentUser = (UserSessionDto) session.getAttribute("user");
        if (currentUser == null) return ResponseEntity.status(401).body("Unauthorized");

        socialService.unfriendUser(currentUser.getId(), targetId);
        return ResponseEntity.ok("Unfriended");
    }
}