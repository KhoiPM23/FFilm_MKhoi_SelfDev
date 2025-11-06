package com.example.project.controller;

import com.example.project.service.AISearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai-search")
public class AISearchController {

    @Autowired
    private AISearchService aiSearchService;

    @PostMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggestMovies(@RequestBody Map<String, String> request) {
        String description = request.get("description");

        if (description == null || description.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Vui lòng nhập mô tả về bộ phim bạn muốn tìm");
            return ResponseEntity.badRequest().body(error);
        }

        if (!aiSearchService.isConfigured()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "AI Search chưa được cấu hình. Vui lòng thêm Gemini API key.");
            return ResponseEntity.status(503).body(error);
        }

        try {
            Map<String, Object> aiResult = aiSearchService.getMovieRecommendation(description);

            if (!Boolean.TRUE.equals(aiResult.get("success"))) {
                String err = aiResult.get("error") != null ? aiResult.get("error").toString() : "AI processing failed";
                throw new Exception(err);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("answer", aiResult.get("answer"));
            response.put("suggestions", aiResult.get("suggestions"));
            response.put("originalDescription", description);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", aiSearchService.isConfigured());
        status.put("provider", "Google Gemini");

        if (!aiSearchService.isConfigured()) {
            status.put("message", "Vui lòng cấu hình Gemini API key trong application.properties (gemini.api.key).");
            status.put("setupUrl", "https://aistudio.google.com/app/apikey");
        }

        return ResponseEntity.ok(status);
    }
}
