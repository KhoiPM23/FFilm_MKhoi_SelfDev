package com.example.project.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private static final String UPLOAD_DIR = "uploads/chat/";
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024L; // 25 MB
    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
            ".exe", ".bat", ".cmd", ".sh", ".jsp", ".jar", ".msi", ".dll", ".com", ".vbs", ".pif", ".scr"
    );

    private boolean isAuthenticated(HttpSession session) {
        if (session == null) return false;
        return session.getAttribute("user") != null
                || session.getAttribute("admin") != null
                || session.getAttribute("moderator") != null
                || session.getAttribute("contentManager") != null;
    }

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file, HttpSession session) {
        if (!isAuthenticated(session)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tệp hình ảnh không được để trống"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dung lượng ảnh vượt quá 25MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Định dạng tệp không phải hình ảnh hợp lệ"));
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.png");
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = UUID.randomUUID().toString() + "-" + safeFilename;
            Path filePath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String fileUrl = "/uploads/chat/" + encodedFileName;
            return ResponseEntity.ok(Map.of("url", fileUrl));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed due to a server error."));
        }
    }

    @PostMapping("/audio")
    public ResponseEntity<?> uploadAudio(@RequestParam("file") MultipartFile file, HttpSession session) {
        if (!isAuthenticated(session)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tệp âm thanh không được để trống"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dung lượng ghi âm vượt quá 25MB"));
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("audio/") && !contentType.toLowerCase().contains("webm") && !contentType.toLowerCase().contains("ogg")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Định dạng tệp không phải âm thanh hợp lệ"));
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.webm");
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = UUID.randomUUID().toString() + "-" + safeFilename;
            Path filePath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String fileUrl = "/uploads/chat/" + encodedFileName;

            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "size", file.getSize()
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed due to a server error."));
        }
    }

    @PostMapping("/file")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, HttpSession session) {
        if (!isAuthenticated(session)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tệp đính kèm không được để trống"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dung lượng tệp vượt quá 25MB"));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.bin");
        String lowerName = originalFilename.toLowerCase();
        for (String dangerous : DANGEROUS_EXTENSIONS) {
            if (lowerName.endsWith(dangerous)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Định dạng tệp thực thi không được phép tải lên"));
            }
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = UUID.randomUUID().toString() + "-" + safeFilename;
            Path filePath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String fileUrl = "/uploads/chat/" + encodedFileName;

            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "fileName", originalFilename,
                "size", file.getSize()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed due to a server error."));
        }
    }
}