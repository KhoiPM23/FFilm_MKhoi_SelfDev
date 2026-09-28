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
    private String replyToId; 
    private SocketMessage replyTo; 

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getSenderAvatar() { return senderAvatar; }
    public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public SocketMessage getReplyTo() { return replyTo; }
    public void setReplyTo(SocketMessage replyTo) { this.replyTo = replyTo; }
}