package com.example.project.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.project.config.VnPayConfig; // Import Config
import com.example.project.dto.UserSessionDto;
import com.example.project.model.Subscription;
import com.example.project.model.SubscriptionPlan;
import com.example.project.service.SubscriptionService;
import com.example.project.service.VnPayService; 

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired 
    private VnPayService vnPayService; 

    // [1. Trang Xác nhận]
    @GetMapping("/confirm/{subId}")
    public String showConfirmPage(
            @PathVariable Integer subId,
            @SessionAttribute("user") UserSessionDto userDto,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (userDto == null) return "redirect:/login";
        
        try {
            Subscription sub = subscriptionService.getSubscriptionById(subId);
            
            if (sub.getUser().getUserID() != userDto.getId()) {
                redirectAttributes.addFlashAttribute("error", "Gói đăng ký không hợp lệ.");
                return "redirect:/subscriptionPlan";
            }
            
            if (sub.isStatus()) {
                redirectAttributes.addFlashAttribute("message", "Gói này đã được kích hoạt.");
                return "redirect:/";
            }

            model.addAttribute("subscription", sub);
            model.addAttribute("plan", sub.getPlan());
            model.addAttribute("subId", subId);
            
            return "service/subscription-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: Không tìm thấy thông tin gói.");
            return "redirect:/subscriptionPlan";
        }
    }
    
    // [2. Tạo Link Thanh toán VNPay] -> SỬA LỖI CHÍNH TẠI ĐÂY
    @GetMapping("/create-link/{subId}")
    public String createPaymentLink(
            @PathVariable Integer subId,
            @SessionAttribute("user") UserSessionDto userDto,
            HttpServletRequest request, 
            RedirectAttributes redirectAttributes) {

        try {
            Subscription sub = subscriptionService.getSubscriptionById(subId);
            
            if (sub.getUser().getUserID() != userDto.getId() || sub.isStatus()) {
                throw new RuntimeException("Gói đăng ký không hợp lệ hoặc đã được thanh toán.");
            }
            
            SubscriptionPlan plan = sub.getPlan();
            
            // Tạo mã giao dịch: kết hợp subId và thời gian để đảm bảo duy nhất mỗi lần click
            // (VNPay yêu cầu TxnRef không trùng lặp)
            String vnp_TxnRef = subId + "_" + VnPayConfig.getRandomNumber(4);
            
            long amount = plan.getPrice().longValue(); 
            String description = "Thanh toan goi FFilm " + plan.getPlanName();
            
            // [FIX] Lấy IP chuẩn từ VnPayConfig
            String userIp = VnPayConfig.getIpAddress(request);
            
            // [FIX] Lấy Base URL động từ request
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

            // Gọi Service với đủ 5 tham số
            String paymentUrl = vnPayService.createPaymentUrl(amount, vnp_TxnRef, userIp, description, baseUrl);
            
            return "redirect:" + paymentUrl;
            
        } catch (RuntimeException | UnsupportedEncodingException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi tạo giao dịch: " + e.getMessage());
            return "redirect:/payment/confirm/" + subId;
        }
    }
    
    // [3. Xử lý Phản hồi VNPay]
    @GetMapping("/vnpay_return")
    public String handleVnPayReturn(HttpServletRequest request, Model model) {
        
        Integer subId = null;
        try {
            // Lấy lại subId từ vnp_TxnRef (dạng: "105_8329")
            String vnp_TxnRef = request.getParameter("vnp_TxnRef");
            if (vnp_TxnRef != null && vnp_TxnRef.contains("_")) {
                subId = Integer.parseInt(vnp_TxnRef.split("_")[0]);
            } else if (vnp_TxnRef != null) {
                subId = Integer.parseInt(vnp_TxnRef);
            }

            // 1. Xác thực chữ ký
            if (!vnPayService.verifyVnPayCallback(request)) {
                 throw new RuntimeException("Chữ ký điện tử không hợp lệ.");
            }
            
            String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");

            // 2. Kiểm tra kết quả (00 là thành công)
            if ("00".equals(vnp_ResponseCode)) {
                Subscription sub = subscriptionService.activateSubscription(subId);
                model.addAttribute("orderCode", vnp_TxnRef);
                model.addAttribute("planName", sub.getPlan().getPlanName());
                model.addAttribute("transactionId", request.getParameter("vnp_TransactionNo"));
                model.addAttribute("totalPrice", request.getParameter("vnp_Amount")); // Chia 100 nếu hiển thị
                model.addAttribute("paymentTime", request.getParameter("vnp_PayDate"));
                
                return "service/payment-success";
            } else {
                throw new RuntimeException("Giao dịch thất bại. Mã lỗi: " + vnp_ResponseCode);
            }
            
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("orderCode", subId);
            return "service/payment-cancel"; 
        }
    }
}