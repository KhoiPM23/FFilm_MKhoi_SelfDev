package com.example.project.controller;

import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [G13] Nâng cấp: Tự động khởi tạo dữ liệu khi ứng dụng khởi động.
 * Bỏ @RestController và triển khai ApplicationRunner.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component // [G13] Đổi từ @RestController thành @Component
public class InitController implements ApplicationRunner { // [G13] Thêm implements

    private static final Logger log = LoggerFactory.getLogger(InitController.class);

    @Autowired(required = false)
    private javax.sql.DataSource dataSource;

    @Autowired
    private MovieService movieService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("...[DataInitializer] Đang chạy trình khởi tạo dữ liệu...");
        if (dataSource != null) {
            try {
                org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
                jdbcTemplate.execute("IF COL_LENGTH('WatchHistory', 'currentTime') IS NULL ALTER TABLE WatchHistory ADD currentTime FLOAT NOT NULL DEFAULT 0;");
            } catch (Exception ex) {
                log.warn("Kiểm tra cột WatchHistory.currentTime: {}", ex.getMessage());
            }
        }
        try {
            // Tự động gọi hàm initGenres
            movieService.initGenres();
            System.out.println("...[DataInitializer] ✅ Khởi tạo Thể loại (Genre) thành công.");
        } catch (Exception e) {
            log.error("Failed to initialize genres during application startup: {}", e.getMessage(), e);
        }
        System.out.println("...[DataInitializer] Trình khởi tạo đã chạy xong.");
    }
    
    // [G13] Toàn bộ hàm @GetMapping("/init-data") cũ đã được xóa
    // vì logic đã được chuyển vào hàm run() ở trên.
}