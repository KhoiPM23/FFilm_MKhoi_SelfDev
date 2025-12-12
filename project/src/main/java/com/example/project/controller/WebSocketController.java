// WebSocketController.java
package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.service.OnlineStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class WebSocketController {
    
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private OnlineStatusService onlineStatusService;
    
    // Typing indicator
    @MessageMapping("/typing")
    public void handleTyping(@Payload Map<String, Object> payload) {
        Integer receiverId = (Integer) payload.get("receiverId");
        Integer senderId = (Integer) payload.get("senderId");
        String senderName = (String) payload.get("senderName");
        
        // Gửi đến người nhận
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/typing",
            Map.of(
                "senderId", senderId,
                "senderName", senderName,
                "type", "TYPING",
                "timestamp", LocalDateTime.now()
            )
        );
    }
    
    // Stop typing
    @MessageMapping("/stop-typing")
    public void handleStopTyping(@Payload Map<String, Object> payload) {
        Integer receiverId = (Integer) payload.get("receiverId");
        
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/typing",
            Map.of("type", "STOP_TYPING")
        );
    }
    
    // Mark as seen
    @MessageMapping("/mark-seen")
    public void handleMarkSeen(@Payload Map<String, Object> payload) {
        Long messageId = Long.valueOf(payload.get("messageId").toString());
        Integer userId = (Integer) payload.get("userId");
        Integer partnerId = (Integer) payload.get("partnerId");
        
        // Cập nhật DB (gọi service)
        // messengerService.markMessageAsSeen(messageId, userId);
        
        // Thông báo cho người gửi
        messagingTemplate.convertAndSendToUser(
            partnerId.toString(),
            "/queue/seen",
            Map.of("messageId", messageId, "seenBy", userId)
        );
    }

    @MessageMapping("/call")
    public void handleCall(@Payload Map<String, Object> payload) {
        String type = (String) payload.get("type");
        Integer receiverId = (Integer) payload.get("receiverId");
        Integer senderId = (Integer) payload.get("senderId");
        
        // Gửi call request đến receiver
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/call",
            Map.of(
                "type", type,
                "senderId", senderId,
                "peerId", payload.get("peerId"),
                "timestamp", LocalDateTime.now()
            )
        );
    }
}