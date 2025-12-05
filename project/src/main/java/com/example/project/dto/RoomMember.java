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
    
    // Trạng thái thiết bị
    private boolean isMuted;
    private boolean isCamOn;
}