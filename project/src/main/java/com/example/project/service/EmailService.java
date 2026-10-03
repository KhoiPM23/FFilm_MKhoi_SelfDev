package com.example.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * EmailService — Dual-mode email sending.
 *
 * <p>Local dev (email.provider=smtp): uses JavaMailSender / Gmail SMTP as before.
 * <p>Production (email.provider=resend): uses Resend HTTP API to bypass Render's
 *    blocked SMTP ports (25, 465, 587). No code change required — only env vars.
 *
 * <p>Required env vars for resend mode:
 * <ul>
 *   <li>EMAIL_PROVIDER=resend
 *   <li>RESEND_API_KEY=re_...
 *   <li>RESEND_FROM_EMAIL=noreply@yourdomain.com (must be verified in Resend)
 * </ul>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.base.url}")
    private String appBaseUrl;

    @Value("${email.provider:smtp}")
    private String emailProvider;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:noreply@ffilm.app}")
    private String resendFromEmail;

    public void sendResetPasswordEmail(String toEmail, String token) {
        String resetLink = appBaseUrl + "/auth/reset-password?token=" + token;
        String subject = "Yêu cầu Đặt lại Mật khẩu";
        String body = "Xin chào,\n\n"
                + "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. "
                + "Vui lòng nhấp vào liên kết dưới đây để tạo mật khẩu mới:\n\n"
                + resetLink + "\n\n"
                + "Liên kết này sẽ hết hạn sau 24 giờ. Nếu bạn không yêu cầu, vui lòng bỏ qua email này.\n\n"
                + "Trân trọng,\n"
                + "Đội ngũ Dịch vụ Khách hàng.";

        if ("resend".equalsIgnoreCase(emailProvider)) {
            sendViaResend(toEmail, subject, body);
        } else {
            sendViaSmtp(toEmail, subject, body);
        }
    }

    // ---- Private helpers ----

    private void sendViaSmtp(String toEmail, String subject, String body) {
        if (mailSender == null) {
            log.warn("[EmailService] JavaMailSender is not configured. Skipping email to {}", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resendFromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[EmailService] SMTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send SMTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    private void sendViaResend(String toEmail, String subject, String body) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("[EmailService] RESEND_API_KEY is not set. Skipping email to {}", toEmail);
            return;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> payload = Map.of(
                    "from", resendFromEmail,
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "text", body
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);
            log.info("[EmailService] Resend API email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send Resend email to {}: {}", toEmail, e.getMessage());
        }
    }
}