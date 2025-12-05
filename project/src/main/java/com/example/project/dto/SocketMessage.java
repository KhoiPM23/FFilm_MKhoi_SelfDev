package com.example.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SocketMessage {
    private String id;           // ID tin nhắn (UUID)
    private String sender;       // Tên người gửi
    private String senderAvatar; // Avatar
    private String content;      // Nội dung text
    private String type;         // CHAT, IMAGE, STICKER, REACTION
    private String timestamp;    
    
    private String mediaUrl;     // URL ảnh hoặc GIF
    
    // --- KHẮC PHỤC LỖI TẠI ĐÂY ---
    
    // 1. Dùng để định danh người nhận trong Chat Riêng (Messenger)
    // Controller sẽ gọi msg.getReplyToId() nên bắt buộc phải có biến này
    private String replyToId; 
    
    // 2. Dùng để chứa thông tin tin nhắn gốc khi Reply (Trích dẫn)
    private SocketMessage replyTo; 
}