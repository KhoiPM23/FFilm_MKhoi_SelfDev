package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.SubscriptionPlan;
import com.example.project.service.SubscriptionPlanService;

import jakarta.servlet.http.HttpSession;

@Controller
public class SubscriptionPlanController {
    @Autowired
    private SubscriptionPlanService planService;

    @GetMapping("/subscriptionPlan")
    public String showSupbscriptionPlan(Model model, HttpSession session) {

        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");

        // ⚠️ BƯỚC 1: KIỂM TRA ĐĂNG NHẬP VÀ LƯU ĐƯỜNG DẪN
        if (userSession == null) {
            // Lưu đường dẫn hiện tại vào session
            session.setAttribute("PREV_URL", "/subscriptionPlan");

            // Chuyển hướng đến trang đăng nhập
            return "redirect:/login";
        }

        List<SubscriptionPlan> subscriptionPlans = planService.showALlPlans();
        model.addAttribute("subscriptionPlans", subscriptionPlans);
        return "service/register-plan";
    }

}
