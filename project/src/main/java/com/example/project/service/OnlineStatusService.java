package com.example.project.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

@Service
public class OnlineStatusService {
    
    private final Map<Integer, LocalDateTime> userLastActive = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> userOnlineStatus = new ConcurrentHashMap<>();
    
    public void markOnline(Integer userId) {
        userOnlineStatus.put(userId, true);
        userLastActive.put(userId, LocalDateTime.now());
    }
    
    public void markOffline(Integer userId) {
        userOnlineStatus.put(userId, false);
        userLastActive.put(userId, LocalDateTime.now());
    }
    
    public boolean isOnline(Integer userId) {
        return userOnlineStatus.getOrDefault(userId, false);
    }
    
    public String getLastActive(Integer userId) {
        LocalDateTime lastActive = userLastActive.get(userId);
        if (lastActive == null) return null;
        
        Duration duration = Duration.between(lastActive, LocalDateTime.now());
        long minutes = duration.toMinutes();
        
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        if (minutes < 1440) return (minutes / 60) + " giờ trước";
        return (minutes / 1440) + " ngày trước";
    }
}