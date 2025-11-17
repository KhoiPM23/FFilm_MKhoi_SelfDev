package com.example.project.config;

import com.example.project.model.User;
import com.example.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminDataInitializer implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("...[DataSeeder] Bắt đầu khởi tạo tài khoản mẫu...");

        // 1. Tạo Admin (Logic cũ)
        createAccountIfNotExist("admin@ffilm.com", "Admin@Ffilm2025!", "System Admin", "admin", "0987654321");

        // 2. Tạo Nhân viên (Staff)
        // Content Manager
        createAccountIfNotExist("content@ffilm.com", "123456", "Content Manager", "content_manager", "0901000001");
        
        // Moderator
        createAccountIfNotExist("mod@ffilm.com", "123456", "Moderator", "moderator", "0902000002");

        // 3. Tạo 6 Users thường
        for (int i = 1; i <= 6; i++) {
            String email = "user" + i + "@gmail.com";
            String name = "User Mẫu " + i;
            String phone = "091234567" + i;
            createAccountIfNotExist(email, "123456", name, "user", phone);
        }

        System.out.println("...[DataSeeder] ✅ Hoàn tất khởi tạo dữ liệu.");
    }

    // Hàm helper: Kiểm tra và tạo tài khoản nếu chưa tồn tại
    private void createAccountIfNotExist(String email, String password, String name, String role, String phone) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setUserName(name);
            user.setEmail(email);
            user.setPassword(password); // Password sẽ được mã hóa bởi @PrePersist trong User entity
            user.setRole(role);
            user.setPhoneNumber(phone);
            // status mặc định là true do @PrePersist

            userRepository.save(user);
            System.out.println("   + Đã tạo: " + email + " (" + role + ")");
        } else {
            System.out.println("   - Bỏ qua: " + email + " (Đã tồn tại)");
        }
    }
}