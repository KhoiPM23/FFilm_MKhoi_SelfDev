package com.example.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // Thay đổi nếu deploy, hoặc cấu hình từ application.properties
    private final String APP_BASE_URL = "http://localhost:8080"; 

    public void sendResetPasswordEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        
        // Link đặt lại mật khẩu sẽ gọi đến GET /auth/reset-password
        String resetLink = APP_BASE_URL + "/auth/reset-password?token=" + token;

        message.setFrom("noreply@yourdomain.com"); 
        message.setTo(toEmail);
        message.setSubject("Yêu cầu Đặt lại Mật khẩu");
        message.setText("Xin chào,\n\n"
                + "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. "
                + "Vui lòng nhấp vào liên kết dưới đây để tạo mật khẩu mới:\n\n"
                + resetLink + "\n\n"
                + "Liên kết này sẽ hết hạn sau 24 giờ. Nếu bạn không yêu cầu, vui lòng bỏ qua email này.\n\n"
                + "Trân trọng,\n"
                + "Đội ngũ Dịch vụ Khách hàng.");

        mailSender.send(message);
    }
}