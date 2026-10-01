// Tạo file: src/main/java/com/example/project/controller/OnlineStatusController.java
package com.example.project.controller;

import com.example.project.dto.UserSessionDto;
import com.example.project.service.OnlineStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class OnlineStatusController {
    
    @Autowired private OnlineStatusService onlineStatusService;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    
    private Integer extractUserId(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.valueOf(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @MessageMapping("/online/ping")
    public void handleOnlinePing(Map<String, Object> payload) {
        if (payload == null) return;
        Integer userId = extractUserId(payload.get("userId"));
        if (userId != null) {
            onlineStatusService.markOnline(userId);
            
            // Broadcast to all friends that this user is online
            messagingTemplate.convertAndSend("/topic/online-status", 
                Map.of(
                    "userId", userId,
                    "isOnline", true,
                    "lastActive", "Vừa xong",
                    "lastActiveTimestamp", System.currentTimeMillis(),
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }
    
    @MessageMapping("/online/status")
    @SendTo("/topic/online-status")
    public Map<String, Object> getOnlineStatus(Map<String, Object> payload) {
        if (payload == null) return Map.of();
        Integer userId = extractUserId(payload.get("userId"));
        if (userId == null) return Map.of();
        boolean isOnline = onlineStatusService.isOnline(userId);
        String lastActive = onlineStatusService.getLastActive(userId);
        Long lastActiveTimestamp = onlineStatusService.getLastActiveMillis(userId);
        
        return Map.of(
            "userId", userId,
            "isOnline", isOnline,
            "lastActive", lastActive != null ? lastActive : "Chưa từng online",
            "lastActiveTimestamp", lastActiveTimestamp != null ? lastActiveTimestamp : 0L
        );
    }
}