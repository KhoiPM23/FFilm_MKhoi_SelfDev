package com.example.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomMember {
    private String sessionId;   // ID của WebSocket Session
    private Integer userId;     // ID trong Database
    private String userName;
    private String avatar;
    private String peerId;      // WebRTC PeerJS ID
    // Trạng thái thiết bị
    private boolean isMuted;
    private boolean isCamOn;
    private long joinedAt = System.currentTimeMillis();

    public RoomMember(String sessionId, Integer userId, String userName, String avatar, String peerId, boolean isMuted, boolean isCamOn) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.userName = userName;
        this.avatar = avatar;
        this.peerId = peerId;
        this.isMuted = isMuted;
        this.isCamOn = isCamOn;
        this.joinedAt = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean isMuted) { this.isMuted = isMuted; }

    public boolean isCamOn() { return isCamOn; }
    public void setCamOn(boolean isCamOn) { this.isCamOn = isCamOn; }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
}