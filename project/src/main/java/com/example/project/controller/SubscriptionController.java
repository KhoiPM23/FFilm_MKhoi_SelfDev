package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.project.dto.SubscriptionPlanRegisterRequest;
import com.example.project.service.SubscriptionService;

@Controller
@RequestMapping("/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // POST đăng ký gói
    @PostMapping("/register")
    public String registerSubscription(
            @RequestParam Integer userId,
            @RequestParam Integer planId,
            RedirectAttributes redirectAttributes) {

        System.out.println("User ID: " + userId); // 1
        System.out.println("Plan ID: " + planId); // Ví dụ: 2
        SubscriptionPlanRegisterRequest planRegisterRequest = new SubscriptionPlanRegisterRequest(userId, planId);
        boolean success = subscriptionService.registerSubscription(planRegisterRequest);

        if (!success) {
            redirectAttributes.addFlashAttribute("message", "Đăng ký thất bại! Chúng tôi sẽ liên hệ với bạn sớm.");
            return "redirect:/subscriptionPlan";
        }

        redirectAttributes.addFlashAttribute("message", "Đăng ký thành công! Chúng tôi sẽ liên hệ với bạn sớm.");
        return "redirect:/";
    }

}
