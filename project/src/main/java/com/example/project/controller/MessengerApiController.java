package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.dto.MessengerDto.MessageDto;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.MessengerMessage;
import com.example.project.model.CallLog;
import com.example.project.service.MessengerService;
import com.example.project.service.OnlineStatusService;
import com.example.project.service.UserService; 
import jakarta.servlet.http.HttpSession;
import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        List<MessengerDto.ConversationDto> conversations = messengerService.getRecentConversations(user.getId());
    
        // ← THÊM logic online
        conversations.forEach(conv -> {
            boolean isOnline = onlineStatusService.isOnline(conv.getPartnerId());
            String lastActive = onlineStatusService.getLastActive(conv.getPartnerId());
            conv.setOnline(isOnline);
            conv.setLastActive(lastActive);
        });
        
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

    // New endpoint for call logs
    @PostMapping("/call-log")
    public ResponseEntity<?> saveCallLog(@RequestBody CallLogRequest request, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        // Save call log to database
        CallLog log = new CallLog();
        log.setUserId(user.getId());
        log.setPartnerId(request.getPartnerId());
        log.setPartnerName(request.getPartnerName());
        log.setCallType(request.getCallType());
        log.setDuration(request.getDuration());
        log.setTimestamp(LocalDateTime.parse(request.getTimestamp()));
        log.setCallStatus("COMPLETED");
        
        // Save to database (you need to create CallLog entity and repository)
        // callLogRepository.save(log);
        
        return ResponseEntity.ok().build();
    }
    
    // Endpoint for message search
    @GetMapping("/search")
    public ResponseEntity<List<MessageDto>> searchMessages(
            @RequestParam Integer partnerId,
            @RequestParam String query,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        List<MessengerMessage> messages = messengerRepository.searchMessages(
            user.getId(), partnerId, query
        );
        
        List<MessageDto> result = messages.stream()
            .map(this::convertToMessageDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // Endpoint for pin/unpin message
    @PostMapping("/pin/{messageId}")
    public ResponseEntity<?> togglePinMessage(@PathVariable Long messageId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        MessengerMessage message = messengerRepository.findById(messageId).orElseThrow();
        
        if (!message.getSender().getId().equals(user.getId()) && 
            !message.getReceiver().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }
        
        message.setPinned(!message.isPinned());
        messengerRepository.save(message);
        
        return ResponseEntity.ok(Map.of("pinned", message.isPinned()));
    }
    
    // Endpoint to get pinned messages
    @GetMapping("/pinned/{partnerId}")
    public ResponseEntity<List<MessageDto>> getPinnedMessages(
            @PathVariable Integer partnerId,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        List<MessengerMessage> pinned = messengerRepository.findPinnedMessages(
            user.getId(), partnerId
        );
        
        List<MessageDto> result = pinned.stream()
            .map(this::convertToMessageDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // DTO for call log request
    @Data
    public static class CallLogRequest {
        private Integer partnerId;
        private String partnerName;
        private String callType;
        private Integer duration;
        private String timestamp;
    }
}