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
import com.example.project.service.BillingService;
import com.example.project.service.SubscriptionService;
import com.example.project.service.VnPayService; 

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;

import com.example.project.model.Payment;
import com.example.project.service.BillingService;
import java.util.Date;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired 
    private VnPayService vnPayService; 

    @Autowired
    private BillingService billingService;

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
        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        // ... (Logic lấy subId giữ nguyên) ...
        if (vnp_TxnRef != null && vnp_TxnRef.contains("_")) {
             subId = Integer.parseInt(vnp_TxnRef.split("_")[0]);
        } else if (vnp_TxnRef != null) {
             subId = Integer.parseInt(vnp_TxnRef);
        }

        try {
            // 1. Validate chữ ký (Giữ nguyên)
            if (!vnPayService.verifyVnPayCallback(request)) {
                 throw new RuntimeException("Chữ ký điện tử không hợp lệ.");
            }
            
            String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");
            String amountStr = request.getParameter("vnp_Amount");
            double amount = amountStr != null ? Double.parseDouble(amountStr) / 100 : 0;

            // Lấy thông tin Subscription để lấy User
            Subscription sub = subscriptionService.getSubscriptionById(subId);

            // TẠO ĐỐI TƯỢNG PAYMENT
            Payment payment = new Payment();
            payment.setAmount(amount);
            payment.setMethod("VNPAY");
            payment.setPaymentDate(new Date());
            payment.setSubscription(sub);
            payment.setUser(sub.getUser());

            if ("00".equals(vnp_ResponseCode)) {
                // THÀNH CÔNG
                subscriptionService.activateSubscription(subId);
                payment.setStatus("SUCCESS"); // Lưu trạng thái thành công
                billingService.savePayment(payment); // <--- LƯU VÀO DB

                model.addAttribute("orderCode", vnp_TxnRef);
                model.addAttribute("planName", sub.getPlan().getPlanName());
                model.addAttribute("transactionId", request.getParameter("vnp_TransactionNo"));
                model.addAttribute("totalPrice", request.getParameter("vnp_Amount")); 
                model.addAttribute("paymentTime", request.getParameter("vnp_PayDate"));
                
                return "service/payment-success";
            } else {
                // THẤT BẠI
                payment.setStatus("FAILED"); // Lưu trạng thái thất bại
                billingService.savePayment(payment); // <--- LƯU VÀO DB
                
                throw new RuntimeException("Giao dịch thất bại. Mã lỗi: " + vnp_ResponseCode);
            }
            
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("orderCode", subId);
            return "service/payment-cancel"; 
        }
    }
    @GetMapping("/history")
    public String showBillingHistory(@SessionAttribute("user") UserSessionDto userDto, Model model) {
        if (userDto == null) return "redirect:/login";

        model.addAttribute("payments", billingService.getPaymentHistory(userDto.getId()));
        model.addAttribute("subscriptions", billingService.getSubscriptionHistory(userDto.getId()));
        
        return "User/billing-history";
    }
}