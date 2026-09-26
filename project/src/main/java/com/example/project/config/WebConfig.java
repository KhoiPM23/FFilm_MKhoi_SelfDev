package com.example.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*") // Cân nhắc giới hạn origin khi deploy
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static resources
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");

        // Dùng URI chuẩn tuyệt đối từ thư mục gốc
        java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads").toAbsolutePath().normalize();
        String uploadPath = uploadDir.toUri().toString();
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.endsWith("/") ? uploadPath : uploadPath + "/");
    }

    // // Helper để map thư mục ngoài vào URL
    // private void exposeDirectory(String dirName, ResourceHandlerRegistry registry) {
    //     Path uploadDir = Paths.get(dirName);
    //     String uploadPath = uploadDir.toFile().getAbsolutePath();

    //     if (dirName.startsWith("../")) dirName = dirName.replace("../", "");

    //     registry.addResourceHandler("/" + dirName + "/**")
    //             .addResourceLocations("file:/" + uploadPath + "/");
    // }
}