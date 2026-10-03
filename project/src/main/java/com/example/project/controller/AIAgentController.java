package com.example.project.controller;

import com.example.project.dto.UserSessionDto; // Import DTO Session
import com.example.project.service.AIAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession; // Import HttpSession
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/ai-agent")
@CrossOrigin(origins = "*")
public class AIAgentController {

    private static final Logger log = LoggerFactory.getLogger(AIAgentController.class);

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

            Map<String, Object> pageContext = null;
            if (json.has("pageContext") && !json.isNull("pageContext")) {
                org.json.JSONObject pc = json.optJSONObject("pageContext");
                if (pc != null) {
                    pageContext = pc.toMap();
                }
            }

            // 1. Lấy thông tin User từ Session (nếu có)
            Integer userId = null;
            UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
            if (userSession != null) {
                userId = userSession.getId();
            }

            // 2. Xử lý tin nhắn (kèm userId & pageContext để cá nhân hóa & grounding)
            Map<String, Object> response = aiAgentService.processMessage(message, conversationId, userId, pageContext);

            // 3. Lưu lịch sử hội thoại (hỗ trợ cả User đăng nhập & Guest theo conversationId)
            String botMsg = (String) response.get("message");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> movies = (List<Map<String, Object>>) response.get("movies");
            aiAgentService.saveChatHistory(conversationId, userId, message, botMsg, movies);

            // 4. Trả về kết quả
            Map<String, Object> finalResponse = new HashMap<>(response);
            finalResponse.put("conversationId", conversationId);

            return ResponseEntity.ok(finalResponse);

        } catch (Exception e) {
            log.error("Error processing AI agent chat message", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Lỗi hệ thống khi xử lý yêu cầu AI"));
        }
    }

    /**
     * Endpoint lấy lịch sử chat
     * GET /api/ai-agent/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        Integer userId = null;
        UserSessionDto userSession = (UserSessionDto) session.getAttribute("user");
        if (userSession != null) {
            userId = userSession.getId();
        }

        String sessionId = (conversationId != null && !conversationId.trim().isEmpty())
                ? conversationId.trim()
                : session.getId();

        List<Map<String, Object>> history = aiAgentService.getChatHistory(sessionId, userId);
        return ResponseEntity.ok(history);
    }

    /**
     * Endpoint xóa lịch sử chat và giải phóng context hội thoại
     * DELETE /api/ai-agent/history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, Object>> clearHistory(
            @RequestParam(required = false) String conversationId,
            HttpSession session) {
        Integer userId = null;
        Object userObj = session.getAttribute("user");
        if (userObj instanceof UserSessionDto) {
            userId = ((UserSessionDto) userObj).getId();
        } else if (userObj instanceof com.example.project.model.User) {
            userId = ((com.example.project.model.User) userObj).getUserID();
        }

        String sessionId = (conversationId != null && !conversationId.trim().isEmpty())
                ? conversationId.trim()
                : session.getId();

        aiAgentService.clearChatHistory(sessionId, userId, conversationId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa toàn bộ lịch sử trò chuyện"));
    }

    /**
     * Endpoint lấy gợi ý câu hỏi chủ động theo Page Context
     * GET /api/ai-agent/suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) Integer movieId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            HttpSession session) {
        Integer userId = null;
        Object userObj = session.getAttribute("user");
        if (userObj instanceof UserSessionDto) {
            userId = ((UserSessionDto) userObj).getId();
        } else if (userObj instanceof com.example.project.model.User) {
            userId = ((com.example.project.model.User) userObj).getUserID();
        }

        Map<String, Object> suggestions = aiAgentService.getProactiveSuggestions(page, movieId, query, genre, userId);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}