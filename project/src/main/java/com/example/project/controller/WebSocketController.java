// WebSocketController.java - SỬA TẤT CẢ CÁC HÀM
package com.example.project.controller;

import com.example.project.service.OnlineStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WebSocketController {
    
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private OnlineStatusService onlineStatusService;
    
    @MessageMapping("/typing")
    public void handleTyping(@Payload Map<String, Object> payload, Principal principal) {
        Integer receiverId = parseInteger(payload.get("receiverId"));
        if (receiverId == null || principal == null) return;
        Integer senderId = parseInteger(principal.getName());
        String senderName = (String) payload.get("senderName");
        
        // Gửi đến người nhận bằng userId
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
    
    @MessageMapping("/stop-typing")
    public void handleStopTyping(@Payload Map<String, Object> payload, Principal principal) {
        Integer receiverId = parseInteger(payload.get("receiverId"));
        if (receiverId == null || principal == null) return;
        
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/typing",
            Map.of("type", "STOP_TYPING")
        );
    }
    
    @MessageMapping("/mark-seen")
    public void handleMarkSeen(@Payload Map<String, Object> payload, Principal principal) {
        if (payload.get("messageId") == null || principal == null) return;
        Long messageId;
        try {
            messageId = Long.valueOf(payload.get("messageId").toString());
        } catch (NumberFormatException e) {
            return;
        }
        Integer userId = parseInteger(principal.getName());
        Integer partnerId = parseInteger(payload.get("partnerId"));
        if (partnerId == null || userId == null) return;
        
        // Thông báo cho người gửi
        messagingTemplate.convertAndSendToUser(
            partnerId.toString(),
            "/queue/seen",
            Map.of("messageId", messageId, "seenBy", userId)
        );
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @MessageMapping("/call")
    public void handleCall(@Payload Map<String, Object> payload, Principal principal) {
        if (payload == null) return;

        String type = (String) payload.get("type");
        Integer receiverId = parseInteger(payload.get("receiverId"));

        Integer senderId = null;
        if (principal != null) {
            try {
                senderId = Integer.valueOf(principal.getName());
            } catch (NumberFormatException ignored) {}
        }

        if (type == null || receiverId == null || senderId == null) {
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", type);
        response.put("senderId", senderId);
        if (payload.get("peerId") != null) {
            response.put("peerId", payload.get("peerId"));
        }
        if (payload.get("callType") != null) {
            response.put("callType", payload.get("callType"));
        }
        if (payload.get("senderName") != null) {
            response.put("senderName", payload.get("senderName"));
        }
        if (payload.get("senderAvatar") != null) {
            response.put("senderAvatar", payload.get("senderAvatar"));
        }
        response.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/call",
            response
        );
    }
}