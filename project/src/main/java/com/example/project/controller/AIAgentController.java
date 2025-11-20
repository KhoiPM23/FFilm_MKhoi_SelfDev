package com.example.project.controller;

import com.example.project.dto.UserSessionDto; // Import DTO Session
import com.example.project.service.AIAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession; // Import HttpSession
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
    public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) String rawBody, HttpSession session) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Request body rỗng"));
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(rawBody);
            String message = json.optString("message", "");
            // ConversationId từ JS chỉ để tham khảo, session thực tế lấy từ HttpSession
            String conversationId = json.optString("conversationId", UUID.randomUUID().toString());

            if (message.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Message không được để trống"));
            }

            // 1. Xử lý tin nhắn
            Map<String, Object> response = aiAgentService.processMessage(message, conversationId);
            
            // 2. Lấy thông tin User từ Session
            Integer userId = null;
            UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
            if (userSession != null) {
                userId = userSession.getId();
            }

            // 3. [BẢO MẬT LỚP 2] Chỉ lưu lịch sử nếu ĐÃ ĐĂNG NHẬP (userId != null)
            if (userId != null) {
                String botMsg = (String) response.get("message");
                List<Map<String, Object>> movies = (List<Map<String, Object>>) response.get("movies");
                aiAgentService.saveChatHistory(conversationId, userId, message, botMsg, movies);
            }

            // 4. Trả về kết quả
            Map<String, Object> finalResponse = new HashMap<>(response);
            finalResponse.put("conversationId", conversationId);
            
            return ResponseEntity.ok(finalResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * [MỚI] Endpoint lấy lịch sử chat
     * GET /api/ai-agent/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(HttpSession session) {
        // Ưu tiên lấy theo User Logged-in
        Integer userId = null;
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        if (userSession != null) {
            userId = userSession.getId();
        }
        
        // Nếu không có User, frontend nên gửi kèm conversationId (nếu muốn support guest history persistent)
        // Nhưng theo yêu cầu hiện tại, ta sẽ dùng userId hoặc session ID tạm
        String sessionId = session.getId(); // JSessionID

        List<Map<String, Object>> history = aiAgentService.getChatHistory(sessionId, userId);
        return ResponseEntity.ok(history);
    }
    
    // ... (Giữ nguyên các endpoint test/health cũ nếu cần) ...
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}