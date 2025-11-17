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

    private final String ADMIN_EMAIL = "admin@ffilm.com";
    private final String ADMIN_PASSWORD = "Admin@Ffilm2025!";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        
        // 1. Kiểm tra xem admin đã tồn tại chưa
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            
            System.out.println("!!! CHƯA TỒN TẠI ADMIN, ĐANG TẠO ADMIN MỚI...");

            User adminUser = new User();
            
            // === ĐẢM BẢO TẤT CẢ CÁC TRƯỜNG @NOTBLANK ĐỀU ĐƯỢC SET ===
            adminUser.setUserName("Admin FFilm");
            adminUser.setEmail(ADMIN_EMAIL); // <-- DÒNG QUAN TRỌNG NHẤT
            adminUser.setPassword(ADMIN_PASSWORD); // (Sẽ được băm bởi @PrePersist)
            adminUser.setRole("admin");
            adminUser.setPhoneNumber("0987654321");
            // adminUser.setStatus(true); // (Trường status sẽ được set tự động bởi @PrePersist)
            // =======================================================

            userRepository.save(adminUser);
            
            System.out.println("✅ TẠO ADMIN THÀNH CÔNG:");
            System.out.println("   Email: " + ADMIN_EMAIL);
            System.out.println("   Password: " + ADMIN_PASSWORD);

        } else {
            System.out.println("... Tài khoản admin đã tồn tại, bỏ qua việc tạo mới.");
        }
    }
}