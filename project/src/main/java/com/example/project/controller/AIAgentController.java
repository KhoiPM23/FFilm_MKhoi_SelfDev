package com.example.project.controller;

import com.example.project.service.AIAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai-agent")
@CrossOrigin(origins = "*")
public class AIAgentController {

    @Autowired
    private AIAgentService aiAgentService;

    /**
     * Main chat endpoint
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) String rawBody) {
        System.out.println("========================================");
        System.out.println("✅ /api/ai-agent/chat HIT!");
        System.out.println("Raw body: " + rawBody);
        System.out.println("========================================");

        if (rawBody == null || rawBody.trim().isEmpty()) {
            System.err.println("❌ Body is null or empty");
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Request body rỗng"
            ));
        }

        try {
            // Parse JSON manually
            System.out.println("🔵 Parsing JSON...");
            org.json.JSONObject json = new org.json.JSONObject(rawBody);
            
            String message = json.optString("message", "");
            String conversationId = json.optString("conversationId", UUID.randomUUID().toString());

            System.out.println("Message extracted: " + message);
            System.out.println("ConversationId: " + conversationId);

            if (message.isEmpty()) {
                System.err.println("❌ Message field is empty");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Message không được để trống"
                ));
            }

            System.out.println("🔵 Calling aiAgentService.processMessage()...");
            Map<String, Object> response = aiAgentService.processMessage(message, conversationId);
            
            System.out.println("🟢 Service returned: " + response);
            
            // IMPORTANT: Create a NEW mutable map instead of modifying immutable one
            Map<String, Object> finalResponse = new HashMap<>(response);
            finalResponse.put("conversationId", conversationId);
            
            System.out.println("✅ SUCCESS! Returning response");
            return ResponseEntity.ok(finalResponse);

        } catch (org.json.JSONException e) {
            System.err.println("❌❌❌ JSON PARSE ERROR ❌❌❌");
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "JSON không hợp lệ: " + e.getMessage()
            ));
            
        } catch (Exception e) {
            System.err.println("❌❌❌ EXCEPTION CAUGHT ❌❌❌");
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            e.printStackTrace();
            
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = "Lỗi không xác định: " + e.getClass().getSimpleName();
            }
            
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", errorMsg
            ));
        }
    }

    /**
     * Test Gemini connection
     */
    @GetMapping("/test-gemini")
    public ResponseEntity<Map<String, Object>> testGemini() {
        System.out.println("🧪 Testing Gemini API...");
        
        try {
            if (!aiAgentService.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Gemini API key chưa cấu hình"
                ));
            }

            Map<String, Object> result = aiAgentService.processMessage("Xin chào", "test");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Gemini hoạt động OK!",
                "response", result.get("message")
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "suggestions", Arrays.asList(
                "FFilm có những gói đăng ký nào?",
                "Làm sao để xem phim chất lượng 4K?",
                "Tôi muốn tìm phim hành động hay",
                "Chính sách hoàn tiền như thế nào?"
            )
        ));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean configured = aiAgentService.isConfigured();
        return ResponseEntity.ok(Map.of(
            "status", configured ? "healthy" : "not_configured",
            "provider", "Google Gemini",
            "configured", configured
        ));
    }

    /**
     * Test endpoint
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("AI Agent Controller is working! ✅");
    }
}