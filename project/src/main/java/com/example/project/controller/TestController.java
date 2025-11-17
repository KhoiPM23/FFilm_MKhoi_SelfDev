package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {
    
    // ================== DASHBOARD ROUTES ==================

    // 1. Dashboard ADMIN
    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "AdminScreen/homeAdminManager";
    }

    // 2. Dashboard CONTENT MANAGER
    @GetMapping("/content/dashboard")
    public String contentDashboard() {
        return "ContentManagerScreen/homeContentManager";
    }

    // 3. Dashboard MODERATOR
    @GetMapping("/moderator/dashboard")
    public String moderatorDashboard() {
        return "ModeratorScreen/homeModeratorManage";
    }

    // ================== CÁC TRANG CHỨC NĂNG ==================

    // Quản lý tài khoản (Admin)
    @GetMapping("/manage-account")
    public String manageAccount() {
        return "AdminScreen/ManageAccount";
    }

    // Quản lý gói dịch vụ (Admin)
    @GetMapping("/admin/manage-plans")
    public String manageSubscriptionPlans() {
        return "AdminScreen/ManagePlans";
    }

    // Quản lý phim (Content Manager)
    @GetMapping("/manage-movies")
    public String manageMovies() {
        return "ContentManagerScreen/ManageMovie";
    }

    // --- CÁC TRANG BẠN CHƯA CÓ HTML (TẠO PLACEHOLDER ĐỂ KHÔNG LỖI 404) ---
    // Khi bấm vào sẽ tạm thời load lại trang dashboard hoặc trang rỗng
    
    @GetMapping("/admin/manage-revenue")
    public String manageRevenue() { return "AdminScreen/homeAdminManager"; } // Tạm

    @GetMapping("/manage-banners")
    public String manageBanners() { return "ContentManagerScreen/homeContentManager"; } // Tạm

    @GetMapping("/manage-comments")
    public String manageComments() { return "ModeratorScreen/homeModeratorManage"; } // Tạm

    @GetMapping("/manage-chat")
    public String manageChat() { return "ModeratorScreen/homeModeratorManage"; } // Tạm
}