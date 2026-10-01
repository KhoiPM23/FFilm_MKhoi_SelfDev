package com.example.project.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

@Service
public class OnlineStatusService {
    
    private final Map<Integer, LocalDateTime> userLastActive = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> userOnlineStatus = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<Integer, String> userNames = new ConcurrentHashMap<>();
    
    public void markOnline(Integer userId) {
        if (userId == null) return;
        userOnlineStatus.put(userId, true);
        userLastActive.put(userId, LocalDateTime.now());
    }

    public boolean addSession(Integer userId, String sessionId) {
        if (userId == null) return false;
        if (sessionId != null && !sessionId.isEmpty()) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
        userOnlineStatus.put(userId, true);
        userLastActive.put(userId, LocalDateTime.now());
        return true;
    }
    
    public void markOffline(Integer userId) {
        if (userId == null) return;
        userSessions.remove(userId);
        userOnlineStatus.put(userId, false);
        userLastActive.put(userId, LocalDateTime.now());
    }

    public boolean removeSession(Integer userId, String sessionId) {
        if (userId == null) return false;
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null && sessionId != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                userOnlineStatus.put(userId, false);
                userLastActive.put(userId, LocalDateTime.now());
                return true; // All sessions closed -> user is fully offline
            }
            return false; // Still has other active sessions (e.g. other tabs)
        }
        userOnlineStatus.put(userId, false);
        userLastActive.put(userId, LocalDateTime.now());
        return true;
    }
    
    public boolean isOnline(Integer userId) {
        if (userId == null) return false;
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            return true;
        }
        Boolean status = userOnlineStatus.get(userId);
        if (status == null) {
            LocalDateTime lastActive = userLastActive.get(userId);
            if (lastActive != null) {
                Duration duration = Duration.between(lastActive, LocalDateTime.now());
                return duration.toMinutes() < 2;
            }
            return false;
        }
        return status;
    }
    
    public String getLastActive(Integer userId) {
        LocalDateTime lastActive = userLastActive.get(userId);
        if (lastActive == null) return "Chưa từng online";
        
        Duration duration = Duration.between(lastActive, LocalDateTime.now());
        long minutes = duration.toMinutes();
        
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        if (minutes < 1440) return (minutes / 60) + " giờ trước";
        return (minutes / 1440) + " ngày trước";
    }

    public Long getLastActiveMillis(Integer userId) {
        LocalDateTime lastActive = userLastActive.get(userId);
        if (lastActive == null) return null;
        return lastActive.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    public void setUserName(Integer userId, String userName) {
        userNames.put(userId, userName);
    }
    
    public String getUserName(Integer userId) {
        return userNames.getOrDefault(userId, "User#" + userId);
    }
}