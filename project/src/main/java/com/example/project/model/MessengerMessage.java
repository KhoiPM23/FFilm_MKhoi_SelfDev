package com.example.project.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messenger_messages")
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
    @Column(name = "isDeleted", columnDefinition = "bit default 0")
    private boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    @Column(name = "is_pinned", columnDefinition = "bit default 0")
    private Boolean isPinned = Boolean.FALSE;
    
    @Column(name = "forwarded_from")
    private String forwardedFrom;
    
    @Column(name = "original_message_id")
    private Long originalMessageId;
    
    @Column(name = "call_duration")
    private Integer callDuration;
    
    @Column(name = "call_status")
    @Enumerated(EnumType.STRING)
    private CallStatus callStatus;

    // No-arg constructor
    public MessengerMessage() {
    }

    // All-args constructor
    public MessengerMessage(Long id, User sender, User receiver, String content, String mediaUrl,
                            MessageType type, MessageStatus status, LocalDateTime timestamp,
                            MessengerMessage replyTo, boolean isDeleted, Boolean isPinned,
                            String forwardedFrom, Long originalMessageId, Integer callDuration,
                            CallStatus callStatus) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
        this.replyTo = replyTo;
        this.isDeleted = isDeleted;
        this.isPinned = isPinned;
        this.forwardedFrom = forwardedFrom;
        this.originalMessageId = originalMessageId;
        this.callDuration = callDuration;
        this.callStatus = callStatus;
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public MessengerMessage getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(MessengerMessage replyTo) {
        this.replyTo = replyTo;
    }

    // boolean primitive: conventional isDeleted getter is "isDeleted"
    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    // For the wrapper Boolean isPinned we provide both getIsPinned() and isPinned()
    // to keep compatibility with existing code that might call either form.
    public Boolean getIsPinned() {
        return isPinned;
    }

    public void setIsPinned(Boolean isPinned) {
        this.isPinned = isPinned;
    }

    public Boolean isPinned() {
        return isPinned;
    }

    public String getForwardedFrom() {
        return forwardedFrom;
    }

    public void setForwardedFrom(String forwardedFrom) {
        this.forwardedFrom = forwardedFrom;
    }

    public Long getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(Long originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    public Integer getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(Integer callDuration) {
        this.callDuration = callDuration;
    }

    public CallStatus getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(CallStatus callStatus) {
        this.callStatus = callStatus;
    }

    // Enums

    public enum CallStatus {
        MISSED, ANSWERED, REJECTED, COMPLETED
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM,
        STICKER,
        AUDIO,
        VIDEO,
        CALL_REQ,
        CALL_ACCEPT,
        CALL_DENY,
        CALL_END
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }
}