package com.example.project.dto;

import com.example.project.model.MessengerMessage.MessageStatus;
import com.example.project.model.MessengerMessage.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class MessengerDto {

    // DTO cho danh sách hội thoại (Cột bên trái)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationDto {
        private Integer partnerId;
        private String partnerName;
        private String partnerAvatar;
        private boolean isOnline; // Để hiển thị chấm xanh

        private String lastMessage;
        private LocalDateTime lastMessageTime;
        private boolean isLastMessageMine; // Để hiện chữ "Bạn: ..."
        private long unreadCount; // Số tin chưa đọc
    }

    // DTO cho từng tin nhắn (Khung chat bên phải)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDto {
        private Long id;
        private Integer senderId;
        private Integer receiverId;
        private String content;
        private String mediaUrl;
        private MessageType type;
        private MessageStatus status;
        private LocalDateTime timestamp;
        private String formattedTime; // Ví dụ: "10:30 AM"
    }
    
    // DTO để gửi tin nhắn từ Client lên
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private Integer receiverId;
        private String content;
        private MessageType type = MessageType.TEXT;
    }
}