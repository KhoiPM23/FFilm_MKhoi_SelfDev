package com.example.project.config;

import com.example.project.dto.UserSessionDto; // Import DTO của bạn
import com.example.project.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
public class WebSocketEventListener {

    @Autowired
    private ChatMessageService chatMessageService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();

        if (attrs != null) {
            String username = (String) attrs.get("username");
            if (username != null) {
                chatMessageService.removeModerator(username);
                System.out.println("⚠️ [DISCONNECT] WebSocket closed for user: " + username);
            }
        }
    }
}