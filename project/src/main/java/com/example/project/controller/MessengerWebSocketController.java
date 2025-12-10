package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * WebSocket Controller for Real-time Features
 */
@Controller
public class MessengerWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Handle typing indicator
     */
    @MessageMapping("/typing")
    public void handleTyping(@Payload Map<String, Object> payload) {
        Integer receiverId = (Integer) payload.get("receiverId");
        Integer senderId = (Integer) payload.get("senderId");
        
        // Send to receiver
        messagingTemplate.convertAndSendToUser(
            String.valueOf(receiverId),
            "/queue/private",
            Map.of("type", "TYPING", "senderId", senderId)
        );
    }

    @MessageMapping("/stop-typing")
    public void handleStopTyping(@Payload Map<String, Object> payload) {
        Integer receiverId = (Integer) payload.get("receiverId");
        
        messagingTemplate.convertAndSendToUser(
            String.valueOf(receiverId),
            "/queue/private",
            Map.of("type", "STOP_TYPING")
        );
    }

    /**
     * Handle message seen status
     */
    @MessageMapping("/mark-seen")
    public void handleMarkSeen(@Payload Map<String, Object> payload) {
        Long messageId = ((Number) payload.get("messageId")).longValue();
        Integer userId = (Integer) payload.get("userId");
        
        // Here you would update DB seen status
        // messengerService.markAsSeen(messageId, userId);
        
        // Notify sender
        messagingTemplate.convertAndSendToUser(
            String.valueOf(userId),
            "/queue/private",
            Map.of("type", "SEEN", "messageId", messageId)
        );
    }
}