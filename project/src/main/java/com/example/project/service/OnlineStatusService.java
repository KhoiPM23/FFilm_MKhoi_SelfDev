package com.example.project.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlineStatusService {
    
    // Lưu userId -> lastSeen timestamp
    private final Map<Integer, LocalDateTime> onlineUsers = new ConcurrentHashMap<>();
    
    // Đánh dấu user online (gọi khi WebSocket connect)
    public void markOnline(Integer userId) {
        onlineUsers.put(userId, LocalDateTime.now());
    }
    
    // Đánh dấu user offline (gọi khi WebSocket disconnect)
    public void markOffline(Integer userId) {
        onlineUsers.remove(userId);
    }
    
    // Kiểm tra user có online không (trong 2 phút gần nhất)
    public boolean isOnline(Integer userId) {
        LocalDateTime lastSeen = onlineUsers.get(userId);
        if (lastSeen == null) return false;
        return LocalDateTime.now().minusMinutes(2).isBefore(lastSeen);
    }
    
    // Lấy thời gian hoạt động gần nhất (format cho UI)
    public String getLastActive(Integer userId) {
        LocalDateTime lastSeen = onlineUsers.get(userId);
        if (lastSeen == null) return "Không hoạt động";
        
        long minutes = java.time.Duration.between(lastSeen, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + "m";
        if (minutes < 1440) return (minutes / 60) + "h";
        return (minutes / 1440) + " ngày";
    }
}