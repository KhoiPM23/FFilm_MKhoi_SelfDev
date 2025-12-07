package com.example.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String content;
    private String link;     // Link điều hướng (vd: /social/profile/5)
    private boolean isRead;
    private String type;     // FRIEND_REQUEST, FRIEND_ACCEPT, LIKE, COMMENT...
    
    // UI Vipro Fields
    private String senderAvatar; // URL avatar người gửi
    private String senderName;   // Tên người gửi
    private String timeAgo;      // "Vừa xong", "5 phút trước" (Tính từ Java)
    private Integer senderId;    // ID để thực hiện Accept/Deny nhanh
}