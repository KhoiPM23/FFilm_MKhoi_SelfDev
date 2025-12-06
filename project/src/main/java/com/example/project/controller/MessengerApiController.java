package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.dto.UserSessionDto; // Hoặc UserDto tùy project bạn
import com.example.project.service.MessengerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messenger")
public class MessengerApiController {

    @Autowired private MessengerService messengerService;

    // 1. API lấy danh sách hội thoại (Cho cột bên trái)
    @GetMapping("/conversations")
    public ResponseEntity<List<MessengerDto.ConversationDto>> getConversations(HttpSession session) {
        // Lấy User từ Session (Sửa lại key 'user' hoặc 'currentUser' tùy login của bạn)
        // Giả sử session lưu object User hoặc UserSessionDto
        Object sessionUser = session.getAttribute("user"); 
        
        if (sessionUser == null) return ResponseEntity.status(401).build();
        
        // Lấy ID an toàn (Cast về đúng kiểu Object trong session của bạn)
        Integer userId = getUserIdFromSession(sessionUser);
        
        return ResponseEntity.ok(messengerService.getRecentConversations(userId));
    }

    // 2. API lấy lịch sử chat (Cho khung bên phải)
    @GetMapping("/chat/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getChatHistory(
            @PathVariable Integer partnerId,
            HttpSession session) {
        Object sessionUser = session.getAttribute("user");
        if (sessionUser == null) return ResponseEntity.status(401).build();

        Integer userId = getUserIdFromSession(sessionUser);
        return ResponseEntity.ok(messengerService.getChatHistory(userId, partnerId));
    }

    // 3. API Gửi tin nhắn
    @PostMapping("/send")
    public ResponseEntity<MessengerDto.MessageDto> sendMessage(
            @RequestBody MessengerDto.SendMessageRequest request,
            HttpSession session) {
        Object sessionUser = session.getAttribute("user");
        if (sessionUser == null) return ResponseEntity.status(401).build();

        Integer userId = getUserIdFromSession(sessionUser);
        return ResponseEntity.ok(messengerService.sendMessage(userId, request));
    }

    // Helper: Lấy ID từ session (Bạn sửa lại cho đúng với class User của bạn)
    private Integer getUserIdFromSession(Object sessionUser) {
        // Ví dụ: Nếu session lưu Entity User
        if (sessionUser instanceof com.example.project.model.User) {
            return ((com.example.project.model.User) sessionUser).getUserID();
        }
        // Ví dụ: Nếu session lưu UserSessionDto
        if (sessionUser instanceof com.example.project.dto.UserSessionDto) {
            return ((com.example.project.dto.UserSessionDto) sessionUser).getId();
        }
        throw new RuntimeException("User session type unknown");
    }
}