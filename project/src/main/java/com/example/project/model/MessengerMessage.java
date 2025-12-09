package com.example.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messenger_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessengerMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    // URL file nếu là ảnh/video
    private String mediaUrl;

    // Loại tin nhắn: TEXT, IMAGE, FILE, SYSTEM
    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.TEXT;

    // Trạng thái: SENT (Đã gửi), DELIVERED (Đã nhận), READ (Đã xem)
    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.SENT;

    private LocalDateTime timestamp;

    // [MỚI] Trỏ đến tin nhắn gốc nếu đây là tin reply
    @ManyToOne
    @JoinColumn(name = "reply_to_id")
    private MessengerMessage replyTo;

    // [MỚI] Cờ đánh dấu đã thu hồi (Soft delete)
    private boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public enum MessageType {
        TEXT, 
        IMAGE, 
        FILE, 
        SYSTEM,
        STICKER, 
        AUDIO,
        VIDEO, // Dành cho file video
        // [MỚI] Tín hiệu cuộc gọi
        CALL_REQ,    // Yêu cầu gọi (kèm PeerID người gọi)
        CALL_ACCEPT, // Chấp nhận (kèm PeerID người nghe)
        CALL_DENY,   // Từ chối
        CALL_END     // Kết thúc
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }
}