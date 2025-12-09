package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.dto.UserSessionDto;
import com.example.project.service.MessengerService;
import com.example.project.service.OnlineStatusService;
import com.example.project.service.UserService; 
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messenger")
public class MessengerApiController {

    @Autowired private MessengerService messengerService;
    @Autowired private UserService userService;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private OnlineStatusService onlineStatusService;

    // 1. API lấy danh sách hội thoại
    @GetMapping("/conversations")
    public ResponseEntity<List<MessengerDto.ConversationDto>> getConversations(HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(messengerService.getRecentConversations(user.getId()));
    }

    // 2. API lấy lịch sử chat
    @GetMapping("/chat/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getChatHistory(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(messengerService.getChatHistory(user.getId(), partnerId));
    }

    // 3. API Gửi tin nhắn (CÓ REALTIME)
    @PostMapping("/send")
    public ResponseEntity<MessengerDto.MessageDto> sendMessage(
            @RequestBody MessengerDto.SendMessageRequest request,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        // 1. Lưu vào DB
        MessengerDto.MessageDto sentMessage = messengerService.sendMessage(user.getId(), request);

        // 2. Bắn Socket cho người nhận (Realtime)
        try {
            // Lấy username người nhận để gửi socket
            // Giả định UserService có hàm lấy username hoặc user entity
            // Nếu chưa có, bạn có thể dùng UserRepository để findById
             String receiverUsername = userService.getUserById(request.getReceiverId()).getUserName();
            
            if (receiverUsername != null) {
                // Gửi tới: /user/{username}/queue/private
                messagingTemplate.convertAndSendToUser(
                    receiverUsername, 
                    "/queue/private", 
                    sentMessage
                );
            }
            
            // 3. Bắn lại cho chính mình (để sync các tab khác)
             messagingTemplate.convertAndSendToUser(
                user.getUserName(),
                "/queue/private",
                sentMessage
            );
            
        } catch (Exception e) {
            e.printStackTrace(); // Log lỗi socket (không chặn flow chính)
        }

        return ResponseEntity.ok(sentMessage);
    }

    // API Thu hồi tin nhắn
    @PostMapping("/unsend/{messageId}")
    public ResponseEntity<?> unsendMessage(@PathVariable Long messageId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session); // Hàm helper cũ
        if (user == null) return ResponseEntity.status(401).build();

        messengerService.unsendMessage(messageId, user.getId());
        
        // Bắn socket báo xóa tin (Client tự xử lý UI xóa) - Optional, làm sau cho đơn giản
        return ResponseEntity.ok().build();
    }

    // Helper: Lấy User từ Session an toàn
    private UserSessionDto getUserFromSession(HttpSession session) {
        Object sessionUser = session.getAttribute("user");
        if (sessionUser instanceof UserSessionDto) {
            return (UserSessionDto) sessionUser;
        }
        return null;
    }

    // [MỚI] API lấy Media cho Sidebar phải
    @GetMapping("/media/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getSharedMedia(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session); // Hàm helper cũ của bạn
        if (user == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(messengerService.getSharedMedia(user.getId(), partnerId));
    }
}