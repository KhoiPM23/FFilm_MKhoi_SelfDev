package com.example.project.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/subscription")
public class SubscriptionController {

    @GetMapping("")
    public String showSubscriptionPage(Model model) {
        // Tạo danh sách các gói dịch vụ
        List<Map<String, Object>> plans = createSubscriptionPlans();
        model.addAttribute("plans", plans);
        
        // Tạo danh sách tính năng chung
        List<String> commonFeatures = createCommonFeatures();
        model.addAttribute("commonFeatures", commonFeatures);
        
        return "subscription";
    }
    
    @PostMapping("/register")
    public String registerSubscription(
            @RequestParam("plan") String planType,
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("fullname") String fullname,
            Model model) {
        
        // Xử lý đăng ký (tạm thời redirect về trang chủ)
        // Trong thực tế, bạn sẽ lưu vào database và xử lý thanh toán
        
        model.addAttribute("message", "Đăng ký thành công! Chúng tôi sẽ liên hệ với bạn sớm.");
        model.addAttribute("planType", planType);
        model.addAttribute("email", email);
        
        return "redirect:/subscription/success";
    }
    
    @GetMapping("/success")
    public String subscriptionSuccess(Model model) {
        return "subscription-success";
    }
    
    private List<Map<String, Object>> createSubscriptionPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();
        
        // Gói Basic
        Map<String, Object> basic = new HashMap<>();
        basic.put("name", "Basic");
        basic.put("price", "70,000");
        basic.put("period", "tháng");
        basic.put("quality", "HD 720p");
        basic.put("devices", "1 thiết bị");
        basic.put("downloads", "Không hỗ trợ");
        basic.put("ads", "Có quảng cáo");
        basic.put("popular", false);
        basic.put("color", "gray");
        plans.add(basic);
        
        // Gói Standard
        Map<String, Object> standard = new HashMap<>();
        standard.put("name", "Standard");
        standard.put("price", "120,000");
        standard.put("period", "tháng");
        standard.put("quality", "Full HD 1080p");
        standard.put("devices", "2 thiết bị");
        standard.put("downloads", "Tải về trên 2 thiết bị");
        standard.put("ads", "Không quảng cáo");
        standard.put("popular", true);
        standard.put("color", "red");
        plans.add(standard);
        
        // Gói Premium
        Map<String, Object> premium = new HashMap<>();
        premium.put("name", "Premium");
        premium.put("price", "180,000");
        premium.put("period", "tháng");
        premium.put("quality", "4K + HDR");
        premium.put("devices", "4 thiết bị");
        premium.put("downloads", "Tải về trên 4 thiết bị");
        premium.put("ads", "Không quảng cáo");
        premium.put("popular", false);
        premium.put("color", "purple");
        plans.add(premium);
        
        return plans;
    }
    
    private List<String> createCommonFeatures() {
        List<String> features = new ArrayList<>();
        features.add("Xem không giới hạn phim và chương trình truyền hình");
        features.add("Đề xuất nội dung dành riêng cho bạn");
        features.add("Hủy hoặc chuyển đổi gói bất cứ lúc nào");
        features.add("Giao diện dễ sử dụng cho mọi lứa tuổi");
        features.add("Phụ đề và lồng tiếng đa ngôn ngữ");
        features.add("Hỗ trợ mọi thiết bị: TV, máy tính, điện thoại, tablet");
        return features;
    }
}



