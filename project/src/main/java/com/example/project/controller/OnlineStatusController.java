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
    
    @MessageMapping("/online/ping")
    public void handleOnlinePing(Map<String, Object> payload) {
        Integer userId = (Integer) payload.get("userId");
        if (userId != null) {
            onlineStatusService.markOnline(userId);
            
            // Broadcast to all friends that this user is online
            messagingTemplate.convertAndSend("/topic/online-status", 
                Map.of(
                    "userId", userId,
                    "isOnline", true,
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }
    
    @MessageMapping("/online/status")
    @SendTo("/topic/online-status")
    public Map<String, Object> getOnlineStatus(Map<String, Object> payload) {
        Integer userId = (Integer) payload.get("userId");
        boolean isOnline = onlineStatusService.isOnline(userId);
        String lastActive = onlineStatusService.getLastActive(userId);
        
        return Map.of(
            "userId", userId,
            "isOnline", isOnline,
            "lastActive", lastActive
        );
    }
}