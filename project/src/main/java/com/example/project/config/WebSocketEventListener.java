// WebSocketEventListener.java - SỬA HOÀN TOÀN
package com.example.project.config;

import com.example.project.service.OnlineStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
public class WebSocketEventListener {
    
    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        String wsSessionId = headerAccessor.getSessionId();
        
        if (sessionAttrs != null) {
            Object userObj = sessionAttrs.get("userSession");
            if (userObj == null) userObj = sessionAttrs.get("userDto");
            if (userObj instanceof com.example.project.dto.UserSessionDto) {
                com.example.project.dto.UserSessionDto user = (com.example.project.dto.UserSessionDto) userObj;
                onlineStatusService.addSession(user.getId(), wsSessionId);
                String httpSessionId = (String) sessionAttrs.get("httpSessionId");
                if (httpSessionId != null) {
                    watchPartyService.cancelPendingDisconnect(httpSessionId);
                }
                try {
                    messagingTemplate.convertAndSend("/topic/online-status", Map.of(
                        "userId", user.getId(),
                        "isOnline", true,
                        "lastActive", "Vừa xong",
                        "lastActiveTimestamp", System.currentTimeMillis(),
                        "timestamp", System.currentTimeMillis()
                    ));
                } catch (Exception ignored) {}
            }
        }
    }
    
    @Autowired
    private com.example.project.service.WatchPartyService watchPartyService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        String wsSessionId = headerAccessor.getSessionId();
        
        if (sessionAttrs != null) {
            Object userObj = sessionAttrs.get("userSession");
            if (userObj == null) userObj = sessionAttrs.get("userDto");
            if (userObj instanceof com.example.project.dto.UserSessionDto) {
                com.example.project.dto.UserSessionDto user = (com.example.project.dto.UserSessionDto) userObj;
                boolean isFullyOffline = onlineStatusService.removeSession(user.getId(), wsSessionId);
                if (isFullyOffline) {
                    try {
                        messagingTemplate.convertAndSend("/topic/online-status", Map.of(
                            "userId", user.getId(),
                            "isOnline", false,
                            "lastActive", "Vừa xong",
                            "lastActiveTimestamp", System.currentTimeMillis(),
                            "timestamp", System.currentTimeMillis()
                        ));
                    } catch (Exception ignored) {}
                }
            }
            
            // Trigger WatchParty room disconnect with 4s grace period to prevent reconnect flickering
            String inRoom = (String) sessionAttrs.get("inWatchPartyRoom");
            String httpSessionId = (String) sessionAttrs.get("httpSessionId");
            if (inRoom != null && httpSessionId != null) {
                watchPartyService.scheduleDisconnect(httpSessionId, 4000);
            }
        }
    }
}