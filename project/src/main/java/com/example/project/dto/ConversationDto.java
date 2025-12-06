package com.example.project.dto;

import lombok.Data;
import java.util.Date;

@Data
public class ConversationDto {
    private Integer partnerId;      // ID người mình đang chat
    private String partnerName;     // Tên hiển thị
    private String partnerAvatar;   // Avatar
    private String lastMessage;     // Nội dung tin nhắn cuối
    private Date lastMessageTime;   // Thời gian nhắn
    private long unreadCount;       // Số tin chưa đọc từ người này
    private String status;          // Trạng thái tin cuối (SENT, DELIVERED, READ)
    private boolean isOnline;       // Trạng thái online (sẽ xử lý ở Bước 3, tạm thời để field)
}