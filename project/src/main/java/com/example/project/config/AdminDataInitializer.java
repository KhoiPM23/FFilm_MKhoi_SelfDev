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
        System.out.println("---------------------------------------------");
        System.out.println("ĐANG KHỞI TẠO DỮ LIỆU MẪU (DATA SEEDING)...");

        // 1. Tạo Admin
        createAccountIfNotFound("admin@gmail.com", "Admin@2025!", "Admin", "ADMIN", "0900000001");

        // 2. Tạo Moderator (Kiểm duyệt viên)
        createAccountIfNotFound("mod@gmail.com", "Mod@2025!", "Moderator", "MODERATOR", "0900000002");

        // 3. Tạo Content Manager (Quản lý nội dung phim)
        createAccountIfNotFound("content@gmail.com", "Content@2025!", "Content Manager", "CONTENT_MANAGER", "0900000003");

        // 4. Tạo User thường
        createAccountIfNotFound("user@gmail.com", "User@2025!", "User", "USER", "0900000004");

        System.out.println("HOÀN TẤT KHỞI TẠO DỮ LIỆU.");
        System.out.println("---------------------------------------------");
    }

    /**
     * Hàm hỗ trợ kiểm tra và tạo tài khoản nếu chưa tồn tại
     */
    private void createAccountIfNotFound(String email, String password, String name, String role, String phone) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            
            // Set các thông tin cơ bản
            user.setUserName(name);
            user.setEmail(email);
            user.setPassword(password); // Password sẽ được mã hóa tự động bởi @PrePersist trong User.java
            user.setRole(role);         // Lưu role IN HOA để khớp với SecurityConfig
            user.setPhoneNumber(phone);
            
            // Status mặc định là true nhờ @PrePersist trong User.java
            
            userRepository.save(user);
            
            System.out.println("   + Đã tạo tài khoản [" + role + "]: " + email);
        } else {
            System.out.println("   - Tài khoản [" + role + "] (" + email + ") đã tồn tại. Bỏ qua.");
        }
    }
}