// WebSocketEventListener.java - SỬA HOÀN TOÀN
package com.example.project.config;

import com.example.project.service.OnlineStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
public class WebSocketEventListener {
    
    @Autowired
    private OnlineStatusService onlineStatusService;
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        
        if (sessionAttrs != null) {
            Object userObj = sessionAttrs.get("userSession");
            if (userObj instanceof com.example.project.dto.UserSessionDto) {
                com.example.project.dto.UserSessionDto user = (com.example.project.dto.UserSessionDto) userObj;
                onlineStatusService.markOnline(user.getId());
                System.out.println("✅ [CONNECT] User online: " + user.getUserName());
            }
        }
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        
        if (sessionAttrs != null) {
            Object userObj = sessionAttrs.get("userSession");
            if (userObj instanceof com.example.project.dto.UserSessionDto) {
                com.example.project.dto.UserSessionDto user = (com.example.project.dto.UserSessionDto) userObj;
                onlineStatusService.markOffline(user.getId());
                System.out.println("⚠️ [DISCONNECT] User offline: " + user.getUserName());
            }
        }
    }
}