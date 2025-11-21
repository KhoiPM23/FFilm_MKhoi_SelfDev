package com.example.project.controller;

import com.example.project.dto.RevenueDashboardDto;
import com.example.project.service.RevenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/revenue")
public class AdminRevenueController {

    @Autowired
    private RevenueService revenueService;

    @GetMapping
    public String showRevenuePage(Model model) {
        // 1. Load Stats
        RevenueDashboardDto stats = revenueService.getDashboardStats();
        model.addAttribute("stats", stats);

        // 2. Load Recent Transactions
        model.addAttribute("recentPayments", revenueService.getRecentTransactions());

        // 3. Load Chart Data
        int currentYear = LocalDate.now().getYear();
        model.addAttribute("chartData", revenueService.getMonthlyChartData(currentYear));
        model.addAttribute("currentYear", currentYear);

        // ✅ Lưu ý: Đảm bảo folder trong src/main/resources/templates là AdminScreen (viết hoa/thường phải khớp)
        return "AdminScreen/manage-revenue";
    }
}