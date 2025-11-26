package com.example.project.service;

import com.example.project.dto.RevenueDashboardDto;
import com.example.project.model.Payment;
import com.example.project.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class RevenueService {
    
    private static final Logger logger = Logger.getLogger(RevenueService.class.getName());

    @Autowired
    private PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public RevenueDashboardDto getDashboardStats() {
        try {
            LocalDate now = LocalDate.now();
            
            Double total = paymentRepository.calculateTotalRevenue();
            Double today = paymentRepository.calculateRevenueToday();
            Double month = paymentRepository.calculateRevenueByMonth(now.getMonthValue(), now.getYear());
            long count = paymentRepository.countAllTransactions();

            // Null-safety check
            total = (total != null) ? total : 0.0;
            today = (today != null) ? today : 0.0;
            month = (month != null) ? month : 0.0;

            return new RevenueDashboardDto(total, today, month, count);
        } catch (Exception e) {
            logger.severe("Lỗi khi lấy Dashboard Stats: " + e.getMessage());
            return new RevenueDashboardDto(0.0, 0.0, 0.0, 0);
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getRecentTransactions() {
        try {
            // Sử dụng method JPA chuẩn đã sửa trong Repository
            List<Payment> payments = paymentRepository.findTop10ByOrderByPaymentDateDesc();
            
            if (payments == null) return new ArrayList<>();
            
            return payments;
        } catch (Exception e) {
            logger.severe("Lỗi khi lấy Recent Transactions: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public double[] getMonthlyChartData(int year) {
        double[] data = new double[12]; 
        try {
            List<Object[]> stats = paymentRepository.getMonthlyRevenueStats(year);
            if (stats != null) {
                for (Object[] row : stats) {
                    if (row.length >= 2 && row[0] != null && row[1] != null) {
                        int month = ((Number) row[0]).intValue();
                        double amount = ((Number) row[1]).doubleValue();
                        if (month >= 1 && month <= 12) {
                            data[month - 1] = amount;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Lỗi chart data: " + e.getMessage());
        }
        return data;
    }
    /**
     * [MỚI] Logic xuất báo cáo CSV tối ưu
     * Sử dụng PrintWriter để stream dữ liệu trực tiếp ra response
     */
    @Transactional(readOnly = true)
    public void exportToCSV(PrintWriter writer) {
        List<Payment> payments = paymentRepository.findAllByOrderByPaymentDateDesc();
        
        // Header của file CSV
        writer.println("Mã GD,Khách Hàng,Email,Gói Cước,Số Tiền (VNĐ),Phương Thức,Ngày GD,Trạng Thái");

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        for (Payment p : payments) {
            try {
                // Xử lý dữ liệu null an toàn
                String id = String.valueOf(p.getPaymentID());
                String userName = (p.getUser() != null) ? escapeCSV(p.getUser().getUserName()) : "Khách vãng lai";
                String email = (p.getUser() != null) ? escapeCSV(p.getUser().getEmail()) : "";
                String plan = (p.getSubscription() != null && p.getSubscription().getPlan() != null) 
                              ? escapeCSV(p.getSubscription().getPlan().getPlanName()) : "N/A";
                String amount = String.format("%.0f", p.getAmount());
                String method = escapeCSV(p.getMethod());
                String date = (p.getPaymentDate() != null) ? dateFormat.format(p.getPaymentDate()) : "";
                String status = parseStatus(p.getStatus());

                // Ghi dòng dữ liệu
                writer.println(String.join(",", id, userName, email, plan, amount, method, date, status));
            } catch (Exception e) {
                logger.warning("Lỗi khi ghi dòng CSV cho payment ID " + p.getPaymentID() + ": " + e.getMessage());
            }
        }
    }

    // Helper: Xử lý ký tự đặc biệt trong CSV (dấu phẩy, xuống dòng)
    private String escapeCSV(String data) {
        if (data == null) return "";
        String escapedData = data.replaceAll("\\R", " "); // Xóa xuống dòng
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    // Helper: Dịch trạng thái sang tiếng Việt
    private String parseStatus(String status) {
        if (status == null) return "Không rõ";
        if (status.equalsIgnoreCase("SUCCESS") || status.equals("00")) return "Thành công";
        if (status.equalsIgnoreCase("FAILED")) return "Thất bại";
        return status;
    }
}