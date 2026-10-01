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
        if (receiverId == null) return;
        Integer senderId = principal != null ? parseInteger(principal.getName()) : parseInteger(payload.get("senderId"));
        String senderName = payload.get("senderName") != null ? (String) payload.get("senderName") : "Người dùng";
        
        Map<String, Object> data = Map.of(
            "senderId", senderId != null ? senderId : 0,
            "senderName", senderName,
            "type", "TYPING",
            "timestamp", LocalDateTime.now()
        );
        messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/typing", data);
        messagingTemplate.convertAndSend("/topic/user." + receiverId + ".typing", data);
    }
    
    @MessageMapping("/stop-typing")
    public void handleStopTyping(@Payload Map<String, Object> payload, Principal principal) {
        Integer receiverId = parseInteger(payload.get("receiverId"));
        if (receiverId == null) return;
        Integer senderId = principal != null ? parseInteger(principal.getName()) : parseInteger(payload.get("senderId"));
        
        Map<String, Object> data = Map.of(
            "senderId", senderId != null ? senderId : 0,
            "type", "STOP_TYPING"
        );
        messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/typing", data);
        messagingTemplate.convertAndSend("/topic/user." + receiverId + ".typing", data);
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
        Map<String, Object> seenData = Map.of("messageId", messageId, "seenBy", userId);
        messagingTemplate.convertAndSendToUser(
            partnerId.toString(),
            "/queue/seen",
            seenData
        );
        messagingTemplate.convertAndSend(
            "/topic/user." + partnerId + ".seen",
            seenData
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
        if (payload.get("callId") != null) {
            response.put("callId", payload.get("callId"));
        }
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
        messagingTemplate.convertAndSend(
            "/topic/user." + receiverId + ".call",
            response
        );
    }

    @MessageMapping("/call-accepted")
    public void handleCallAccepted(@Payload Map<String, Object> payload, Principal principal) {
        if (payload == null) return;
        Integer receiverId = parseInteger(payload.get("receiverId"));
        Integer senderId = null;
        if (principal != null) {
            try {
                senderId = Integer.valueOf(principal.getName());
            } catch (NumberFormatException ignored) {}
        }
        if (receiverId == null || senderId == null) return;

        Map<String, Object> response = new HashMap<>();
        response.put("type", "CALL_ACCEPT");
        response.put("senderId", senderId);
        if (payload.get("peerId") != null) {
            response.put("peerId", payload.get("peerId"));
        }
        response.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/call", response);
        messagingTemplate.convertAndSend("/topic/user." + receiverId + ".call", response);
    }
}