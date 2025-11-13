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
@Component // [G13] Đổi từ @RestController thành @Component
public class InitController implements ApplicationRunner { // [G13] Thêm implements

    @Autowired
    private MovieService movieService;

    /**
     * [G13] Hàm này sẽ tự động chạy MỘT LẦN
     * ngay sau khi Spring Boot khởi động xong.
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("...[DataInitializer] Đang chạy trình khởi tạo dữ liệu...");
        try {
            // Tự động gọi hàm initGenres
            movieService.initGenres();
            System.out.println("...[DataInitializer] ✅ Khởi tạo Thể loại (Genre) thành công.");
        } catch (Exception e) {
            System.err.println("...[DataInitializer] ❌ LỖI khi khởi tạo Thể loại: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("...[DataInitializer] Trình khởi tạo đã chạy xong.");
    }
    
    // [G13] Toàn bộ hàm @GetMapping("/init-data") cũ đã được xóa
    // vì logic đã được chuyển vào hàm run() ở trên.
}