package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.project.dto.SubscriptionPlanRegisterRequest;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.User;
import com.example.project.service.SubscriptionService;

@Controller
@RequestMapping("/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // POST đăng ký gói (Điểm tiếp nối luồng mới)
    @PostMapping("/register")
    public String registerSubscription(
            @SessionAttribute("user") UserSessionDto userDto,
            @RequestParam Integer planId,
            RedirectAttributes redirectAttributes) {
        if (userDto == null) {
            return "redirect:/login";
        }
        Integer userId = userDto.getId();
        SubscriptionPlanRegisterRequest planRegisterRequest = new SubscriptionPlanRegisterRequest(userId, planId);
        
        try {
            // Gọi hàm đăng ký Pending mới
            Integer subId = subscriptionService.registerPendingSubscription(planRegisterRequest); 

            if (subId == null) {
                redirectAttributes.addFlashAttribute("error", "Đăng ký thất bại! Không tìm thấy user hoặc gói.");
                return "redirect:/subscriptionPlan";
            }
            
            // Chuyển hướng đến trang xác nhận thanh toán (điểm tiếp nối)
            return "redirect:/payment/confirm/" + subId; 
            
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/subscriptionPlan";
        }
    }

} 