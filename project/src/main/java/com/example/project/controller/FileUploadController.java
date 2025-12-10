package com.example.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private static final String UPLOAD_DIR = "uploads/chat/";

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // [FIX] Làm sạch tên file để tránh ký tự đặc biệt
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            // Thay thế space và ký tự đặc biệt bằng dấu gạch dưới
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            
            String fileName = UUID.randomUUID().toString() + "-" + safeFilename;
            Path filePath = uploadPath.resolve(fileName);
            
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // [CRITICAL] URL encode tên file để tránh lỗi 400 khi tải về
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20"); // Fix space encoding
            
            String fileUrl = "/uploads/chat/" + encodedFileName;
            return ResponseEntity.ok(Map.of("url", fileUrl));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/audio")
    public ResponseEntity<?> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = UUID.randomUUID().toString() + "-" + safeFilename;
            Path filePath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

            String fileUrl = "/uploads/chat/" + encodedFileName;

            // Return url and size (duration server-side would require media lib)
            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "size", file.getSize()
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }
}