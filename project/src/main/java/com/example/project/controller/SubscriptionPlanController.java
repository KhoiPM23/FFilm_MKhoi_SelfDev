package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.project.model.SubscriptionPlan;
import com.example.project.service.SubscriptionPlanService;

@Controller
public class SubscriptionPlanController {
    @Autowired
    private SubscriptionPlanService planService;

    @GetMapping("/subscriptionPlan")
    public String showSupbscriptionPlan(Model model) {

        List<SubscriptionPlan> subscriptionPlans = planService.showALlPlans();
        model.addAttribute("subscriptionPlans", subscriptionPlans);
        return "service/register-plan";
    }

}
