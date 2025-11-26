package com.example.project.controller;

import com.example.project.dto.RevenueDashboardDto;
import com.example.project.service.RevenueService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    /**
     * [MỚI] Endpoint xuất báo cáo
     */
    @GetMapping("/export")
    public void exportRevenueReport(HttpServletResponse response) throws IOException {
        // Cấu hình Header để tải file
        String filename = "Bao_Cao_Doanh_Thu_" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy")) + ".csv";
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        // [QUAN TRỌNG] Thêm BOM (Byte Order Mark) để Excel nhận diện đúng tiếng Việt (UTF-8)
        response.getWriter().write('\uFEFF');

        // Gọi service ghi dữ liệu vào response writer
        revenueService.exportToCSV(response.getWriter());
    }
}