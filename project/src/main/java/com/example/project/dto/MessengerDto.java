package com.example.project.dto;

import com.example.project.model.MessengerMessage.MessageStatus;
import com.example.project.model.MessengerMessage.MessageType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO classes for Messenger feature.
 * Explicit getters/setters used (not Lombok @Data) because Lombok annotation
 * processing for nested static inner classes is unreliable in this build config.
 */
public class MessengerDto {

    // DTO cho danh sách hội thoại (Cột bên trái)
    public static class ConversationDto {
        private Integer partnerId;
        private String partnerName;
        private String partnerAvatar;
        private boolean isOnline;
        private String lastMessage;
        private LocalDateTime lastMessageTime;
        private boolean isLastMessageMine;
        private long unreadCount;
        private String timeAgo;
        private boolean isRead;
        private String statusClass;
        private boolean friend;
        private String relationStatus;
        private String lastActive;
        private Long lastActiveTimestamp;

        public ConversationDto() {}

        public Integer getPartnerId() { return partnerId; }
        public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }
        public String getPartnerName() { return partnerName; }
        public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
        public String getPartnerAvatar() { return partnerAvatar; }
        public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }
        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { isOnline = online; }
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        public LocalDateTime getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
        public boolean isLastMessageMine() { return isLastMessageMine; }
        public void setLastMessageMine(boolean lastMessageMine) { isLastMessageMine = lastMessageMine; }
        public long getUnreadCount() { return unreadCount; }
        public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
        public String getTimeAgo() { return timeAgo; }
        public void setTimeAgo(String timeAgo) { this.timeAgo = timeAgo; }
        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
        public String getStatusClass() { return statusClass; }
        public void setStatusClass(String statusClass) { this.statusClass = statusClass; }
        public boolean isFriend() { return friend; }
        public void setFriend(boolean friend) { this.friend = friend; }
        public String getRelationStatus() { return relationStatus; }
        public void setRelationStatus(String relationStatus) { this.relationStatus = relationStatus; }
        public String getLastActive() { return lastActive; }
        public void setLastActive(String lastActive) { this.lastActive = lastActive; }
        public Long getLastActiveTimestamp() { return lastActiveTimestamp; }
        public void setLastActiveTimestamp(Long lastActiveTimestamp) { this.lastActiveTimestamp = lastActiveTimestamp; }
    }

    // DTO cho từng tin nhắn (Khung chat bên phải)
    public static class MessageDto {
        private Long id;
        private Integer senderId;
        private Integer receiverId;
        private String content;
        private String mediaUrl;
        private MessageType type;
        private MessageStatus status;
        private LocalDateTime timestamp;
        private String formattedTime;
        private String senderAvatar;
        private boolean isDeleted;
        private MessageDto replyTo;
        private Boolean isPinned;
        private Boolean isEdited;
        private Map<String, Integer> reactions;
        private String userReaction;
        private Integer callDuration;
        private String callStatus;

        public MessageDto() {}
        public MessageDto(Long id, Integer senderId, Integer receiverId, String content, String mediaUrl,
                          MessageType type, MessageStatus status, LocalDateTime timestamp, String formattedTime,
                          String senderAvatar, boolean isDeleted, MessageDto replyTo, Boolean isPinned,
                          Boolean isEdited, Map<String, Integer> reactions, String userReaction,
                          Integer callDuration, String callStatus) {
            this.id = id; this.senderId = senderId; this.receiverId = receiverId; this.content = content;
            this.mediaUrl = mediaUrl; this.type = type; this.status = status; this.timestamp = timestamp;
            this.formattedTime = formattedTime; this.senderAvatar = senderAvatar; this.isDeleted = isDeleted;
            this.replyTo = replyTo; this.isPinned = isPinned; this.isEdited = isEdited;
            this.reactions = reactions; this.userReaction = userReaction;
            this.callDuration = callDuration; this.callStatus = callStatus;
        }

        public static MessageDtoBuilder builder() { return new MessageDtoBuilder(); }
        public static class MessageDtoBuilder {
            private Long id; private Integer senderId; private Integer receiverId; private String content;
            private String mediaUrl; private MessageType type; private MessageStatus status;
            private LocalDateTime timestamp; private String formattedTime; private String senderAvatar;
            private boolean isDeleted; private MessageDto replyTo; private Boolean isPinned;
            private Boolean isEdited; private Map<String, Integer> reactions; private String userReaction;
            private Integer callDuration; private String callStatus;
            public MessageDtoBuilder id(Long id) { this.id = id; return this; }
            public MessageDtoBuilder senderId(Integer senderId) { this.senderId = senderId; return this; }
            public MessageDtoBuilder receiverId(Integer receiverId) { this.receiverId = receiverId; return this; }
            public MessageDtoBuilder content(String content) { this.content = content; return this; }
            public MessageDtoBuilder mediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; return this; }
            public MessageDtoBuilder type(MessageType type) { this.type = type; return this; }
            public MessageDtoBuilder status(MessageStatus status) { this.status = status; return this; }
            public MessageDtoBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public MessageDtoBuilder formattedTime(String formattedTime) { this.formattedTime = formattedTime; return this; }
            public MessageDtoBuilder senderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; return this; }
            public MessageDtoBuilder isDeleted(boolean isDeleted) { this.isDeleted = isDeleted; return this; }
            public MessageDtoBuilder replyTo(MessageDto replyTo) { this.replyTo = replyTo; return this; }
            public MessageDtoBuilder isPinned(Boolean isPinned) { this.isPinned = isPinned; return this; }
            public MessageDtoBuilder isEdited(Boolean isEdited) { this.isEdited = isEdited; return this; }
            public MessageDtoBuilder reactions(Map<String, Integer> reactions) { this.reactions = reactions; return this; }
            public MessageDtoBuilder userReaction(String userReaction) { this.userReaction = userReaction; return this; }
            public MessageDtoBuilder callDuration(Integer callDuration) { this.callDuration = callDuration; return this; }
            public MessageDtoBuilder callStatus(String callStatus) { this.callStatus = callStatus; return this; }
            public MessageDto build() {
                return new MessageDto(id, senderId, receiverId, content, mediaUrl, type, status, timestamp,
                    formattedTime, senderAvatar, isDeleted, replyTo, isPinned, isEdited, reactions, userReaction,
                    callDuration, callStatus);
            }
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getSenderId() { return senderId; }
        public void setSenderId(Integer senderId) { this.senderId = senderId; }
        public Integer getReceiverId() { return receiverId; }
        public void setReceiverId(Integer receiverId) { this.receiverId = receiverId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
        public MessageType getType() { return type; }
        public void setType(MessageType type) { this.type = type; }
        public MessageStatus getStatus() { return status; }
        public void setStatus(MessageStatus status) { this.status = status; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getFormattedTime() { return formattedTime; }
        public void setFormattedTime(String formattedTime) { this.formattedTime = formattedTime; }
        public String getSenderAvatar() { return senderAvatar; }
        public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }
        public boolean isDeleted() { return isDeleted; }
        public void setDeleted(boolean deleted) { isDeleted = deleted; }
        public MessageDto getReplyTo() { return replyTo; }
        public void setReplyTo(MessageDto replyTo) { this.replyTo = replyTo; }
        public Boolean getIsPinned() { return isPinned; }
        public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
        public Boolean getIsEdited() { return isEdited; }
        public void setIsEdited(Boolean isEdited) { this.isEdited = isEdited; }
        public Map<String, Integer> getReactions() { return reactions; }
        public void setReactions(Map<String, Integer> reactions) { this.reactions = reactions; }
        public String getUserReaction() { return userReaction; }
        public void setUserReaction(String userReaction) { this.userReaction = userReaction; }
        public Integer getCallDuration() { return callDuration; }
        public void setCallDuration(Integer callDuration) { this.callDuration = callDuration; }
        public String getCallStatus() { return callStatus; }
        public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
    }

    public static class SendMessageRequest {
        private Integer receiverId;
        private String content;
        private MessageType type = MessageType.TEXT;
        private Long replyToId;

        public SendMessageRequest() {}
        public SendMessageRequest(Integer receiverId, String content, MessageType type, Long replyToId) {
            this.receiverId = receiverId; this.content = content;
            this.type = type; this.replyToId = replyToId;
        }
        public Integer getReceiverId() { return receiverId; }
        public void setReceiverId(Integer receiverId) { this.receiverId = receiverId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public MessageType getType() { return type; }
        public void setType(MessageType type) { this.type = type; }
        public Long getReplyToId() { return replyToId; }
        public void setReplyToId(Long replyToId) { this.replyToId = replyToId; }
    }

    public static class EditMessageRequest {
        private String content;
        public EditMessageRequest() {}
        public EditMessageRequest(String content) { this.content = content; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}