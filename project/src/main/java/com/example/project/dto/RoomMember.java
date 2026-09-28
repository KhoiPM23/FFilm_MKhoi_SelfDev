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
}