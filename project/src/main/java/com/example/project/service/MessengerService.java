package com.example.project.service;

import com.example.project.dto.MessengerDto;
import com.example.project.model.MessengerMessage.MessageType;
import com.example.project.model.FriendRequest; // [MỚI] Thêm import này
import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.MessengerRepository;
import com.example.project.repository.UserRepository;
import com.example.project.repository.CallLogRepository;
import com.example.project.repository.ConversationSettingsRepository;
import com.example.project.model.CallLog;
import com.example.project.model.ConversationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessengerService {

    @Autowired private MessengerRepository messengerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendRequestRepository friendRequestRepository;
    @Autowired private CallLogRepository callLogRepository;
    @Autowired private ConversationSettingsRepository conversationSettingsRepository;
    @Autowired(required = false) private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    // ============= FIX 1: Thêm các phương thức mới =============
    
    public List<MessengerMessage> searchMessages(Integer userId, Integer partnerId, String query) {
        return messengerRepository.searchMessages(userId, partnerId, query);
    }
    
    public List<MessengerMessage> getPinnedMessages(Integer userId, Integer partnerId) {
        return messengerRepository.findPinnedMessages(userId, partnerId);
    }
    
    public MessengerMessage getMessageById(Long messageId) {
        return messengerRepository.findById(messageId).orElseThrow(
            () -> new RuntimeException("Message not found with id: " + messageId)
        );
    }
    
    public void saveMessage(MessengerMessage message) {
        messengerRepository.save(message);
    }
    
    public Map<String, Object> getChatStats(Integer userId, Integer partnerId) {
        Map<String, Object> stats = new HashMap<>();

        User user1 = userRepository.findById(userId).orElseThrow();
        User user2 = userRepository.findById(partnerId).orElseThrow();

        // Đếm tổng số tin nhắn tại tầng database
        long totalMessages = messengerRepository.countConversationMessages(user1, user2);
        stats.put("totalMessages", totalMessages);

        // Đếm media (khác TEXT) tại tầng database
        long mediaCount = messengerRepository.countConversationMediaMessages(user1, user2, MessengerMessage.MessageType.TEXT);
        stats.put("mediaCount", mediaCount);

        // Tin nhắn đầu tiên (chỉ load 1 entity thay vì toàn bộ lịch sử)
        List<MessengerMessage> firstMessageList = messengerRepository.findFirstMessageInConversation(
            user1, user2, org.springframework.data.domain.PageRequest.of(0, 1)
        );

        if (!firstMessageList.isEmpty()) {
            MessengerMessage firstMessage = firstMessageList.get(0);
            Map<String, Object> firstMsgInfo = new HashMap<>();
            firstMsgInfo.put("id", firstMessage.getId());
            firstMsgInfo.put("content", firstMessage.getContent());
            firstMsgInfo.put("timestamp", firstMessage.getTimestamp());
            firstMsgInfo.put("sender", firstMessage.getSender().getUserName());
            stats.put("firstMessage", firstMsgInfo);
        }
        
        // Tin nhắn thường xuyên nhất (chưa implement, cần thêm repository method)
        // stats.put("mostActiveHour", getMostActiveHour(allMessages));
        
        return stats;
    }

    // CallLog methods
    public void saveCallLog(CallLog log) {
        callLogRepository.save(log);
    }

    public List<CallLog> getCallLogsByPartner(Integer userId, Integer partnerId) {
        return callLogRepository.findByUserIdAndPartnerIdOrderByTimestampDesc(userId, partnerId);
    }

    public List<CallLog> getRecentCalls(Integer userId, LocalDateTime fromDate) {
        return callLogRepository.findRecentCalls(userId, fromDate);
    }

    public Long countMissedCallsSince(Integer userId, LocalDateTime since) {
        return callLogRepository.countMissedCallsSince(userId, since);
    }

    @Transactional
    public MessengerDto.MessageDto recordCallMessage(Integer callerId, Integer calleeId, String callType, String statusStr, int duration, String callId) {
        String lockKey = ("CALL_LOCK_" + (callId != null && !callId.trim().isEmpty() ? callId.trim() : (callerId + "_" + calleeId))).intern();
        synchronized (lockKey) {
            User caller = userRepository.findById(callerId)
                    .orElseThrow(() -> new RuntimeException("Caller not found: " + callerId));
            User callee = userRepository.findById(calleeId)
                    .orElseThrow(() -> new RuntimeException("Callee not found: " + calleeId));

            // Idempotency check if callId is provided
            if (callId != null && !callId.trim().isEmpty()) {
                List<MessengerMessage> existingByCallId = messengerRepository.findByCallIdInMetadata(callId.trim());
                if (existingByCallId != null && !existingByCallId.isEmpty()) {
                    return convertToMessageDto(existingByCallId.get(0)); // Already recorded!
                }
                List<MessengerMessage> existing = messengerRepository.findConversation(caller, callee);
                if (existing != null) {
                    for (MessengerMessage em : existing) {
                        if (em.getMetadata() != null && em.getMetadata().contains(callId)) {
                            return convertToMessageDto(em); // Already recorded!
                        }
                    }
                }
            }

        MessengerMessage.CallStatus status;
        try {
            status = MessengerMessage.CallStatus.valueOf(statusStr.toUpperCase());
        } catch (Exception e) {
            status = MessengerMessage.CallStatus.COMPLETED;
        }

        boolean isVideo = "VIDEO".equalsIgnoreCase(callType);
        String contentText;
        if (status == MessengerMessage.CallStatus.MISSED) {
            contentText = isVideo ? "Cuộc gọi video nhỡ" : "Cuộc gọi thoại nhỡ";
        } else if (status == MessengerMessage.CallStatus.REJECTED) {
            contentText = isVideo ? "Cuộc gọi video bị từ chối" : "Cuộc gọi thoại bị từ chối";
        } else {
            contentText = isVideo ? "Cuộc gọi video" : "Cuộc gọi thoại";
        }

        String metadataJson = "{\"callId\":\"" + (callId != null ? callId : "") + "\",\"isVideo\":" + isVideo + "}";

        MessengerMessage message = new MessengerMessage();
        message.setSender(caller);
        message.setReceiver(callee);
        message.setType(MessengerMessage.MessageType.CALL_END);
        message.setMediaUrl(isVideo ? "VIDEO" : "AUDIO");
        message.setContent(contentText);
        message.setStatus(MessengerMessage.MessageStatus.SENT);
        message.setTimestamp(LocalDateTime.now());
        message.setCallDuration(duration);
        message.setCallStatus(status);
        message.setMetadata(metadataJson);

        MessengerMessage saved = messengerRepository.save(message);

        // Also save CallLog for general call log history
        try {
            CallLog log = new CallLog();
            log.setUserId(callerId);
            log.setPartnerId(calleeId);
            log.setPartnerName(callee.getUserName());
            log.setCallType(CallLog.CallType.OUTGOING);
            log.setDuration(duration);
            log.setTimestamp(saved.getTimestamp());
            log.setCallStatus(status == MessengerMessage.CallStatus.MISSED ? CallLog.CallStatus.MISSED :
                             (status == MessengerMessage.CallStatus.REJECTED ? CallLog.CallStatus.REJECTED : CallLog.CallStatus.COMPLETED));
            log.setVideo(isVideo);
            log.setInitiatorId(callerId);
            callLogRepository.save(log);

            CallLog calleeLog = new CallLog();
            calleeLog.setUserId(calleeId);
            calleeLog.setPartnerId(callerId);
            calleeLog.setPartnerName(caller.getUserName());
            calleeLog.setCallType(status == MessengerMessage.CallStatus.MISSED ? CallLog.CallType.MISSED : CallLog.CallType.INCOMING);
            calleeLog.setDuration(duration);
            calleeLog.setTimestamp(saved.getTimestamp());
            calleeLog.setCallStatus(status == MessengerMessage.CallStatus.MISSED ? CallLog.CallStatus.MISSED :
                                   (status == MessengerMessage.CallStatus.REJECTED ? CallLog.CallStatus.REJECTED : CallLog.CallStatus.COMPLETED));
            calleeLog.setVideo(isVideo);
            calleeLog.setInitiatorId(callerId);
            callLogRepository.save(calleeLog);
        } catch (Exception ex) {
            // Non-fatal
        }

        MessengerDto.MessageDto dto = convertToMessageDto(saved);

        // Broadcast STOMP event in real time to both participants
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSendToUser(callerId.toString(), "/queue/private", dto);
                messagingTemplate.convertAndSendToUser(calleeId.toString(), "/queue/private", dto);
                messagingTemplate.convertAndSend("/topic/user." + callerId + ".private", dto);
                messagingTemplate.convertAndSend("/topic/user." + calleeId + ".private", dto);
            } catch (Exception ignored) {}
        }

        return dto;
        }
    }

    // ConversationSettings methods
    public Optional<ConversationSettings> getConversationSettings(Integer userId, Integer partnerId) {
        return conversationSettingsRepository.findByUserIdAndPartnerId(userId, partnerId);
    }

    public ConversationSettings saveConversationSettings(ConversationSettings settings) {
        return conversationSettingsRepository.save(settings);
    }

    @Transactional
    public int updateThemeColor(Integer userId, Integer partnerId, String themeColor) {
        return conversationSettingsRepository.updateThemeColor(userId, partnerId, themeColor);
    }

    @Transactional
    public int updateNickname(Integer userId, Integer partnerId, String nickname) {
        return conversationSettingsRepository.updateNickname(userId, partnerId, nickname);
    }

    // 1. Lấy danh sách hội thoại
    public List<MessengerDto.ConversationDto> getRecentConversations(Integer currentUserId) {
        Map<Integer, MessengerDto.ConversationDto> map = new LinkedHashMap<>();

        User me = userRepository.findById(currentUserId).orElse(null);
        if (me == null) return new ArrayList<>();

        Map<Integer, ConversationSettings> settingsMap = new HashMap<>();
        try {
            List<ConversationSettings> settingsList = conversationSettingsRepository.findByUserId(currentUserId);
            if (settingsList != null) {
                for (ConversationSettings s : settingsList) {
                    if (s.getPartnerId() != null) {
                        settingsMap.put(s.getPartnerId(), s);
                    }
                }
            }
        } catch (Exception ignored) {}

        // BƯỚC 1: Lấy tin nhắn và bạn bè như cũ
        List<MessengerMessage> messages = messengerRepository.findAllMessagesByUser(currentUserId);
        
        for (MessengerMessage msg : messages) {
            boolean isSender = msg.getSender().getUserID() == currentUserId;
            User partner = isSender ? msg.getReceiver() : msg.getSender();
            
            if (!map.containsKey(partner.getUserID())) {
                ConversationSettings s = settingsMap.get(partner.getUserID());
                MessengerDto.ConversationDto dto = createConversationDto(me, partner, msg, isSender, s);
                map.put(partner.getUserID(), dto);
            }
        }

        // BƯỚC 2: Merge thêm bạn bè chưa từng chat
        List<FriendRequest> friendRequests = friendRequestRepository.findAllAcceptedByUserId(currentUserId);
        
        for (FriendRequest fr : friendRequests) {
            User friend = fr.getSender().getUserID() == currentUserId ? 
                fr.getReceiver() : fr.getSender();
            
            if (!map.containsKey(friend.getUserID())) {
                MessengerDto.ConversationDto dto = new MessengerDto.ConversationDto();
                dto.setPartnerId(friend.getUserID());
                String friendName = friend.getUserName();
                ConversationSettings s = settingsMap.get(friend.getUserID());
                if (s != null && s.getNickname() != null && !s.getNickname().trim().isEmpty()) {
                    friendName = s.getNickname().trim();
                }
                dto.setPartnerName(friendName);
                dto.setPartnerAvatar(generateAvatar(friend.getUserName()));
                dto.setLastMessage("Các bạn đã là bạn bè trên FFilm");
                dto.setLastMessageTime(LocalDateTime.now());
                dto.setTimeAgo("");
                dto.setUnreadCount(0);
                dto.setRead(true);
                dto.setLastMessageMine(false);
                dto.setFriend(true);
                dto.setRelationStatus("FRIEND");
                
                map.put(friend.getUserID(), dto);
            }
        }

        return new ArrayList<>(map.values());
    }
    
    private MessengerDto.ConversationDto createConversationDto(User me, User partner, MessengerMessage lastMsg, boolean isSender, ConversationSettings settings) {
        MessengerDto.ConversationDto dto = new MessengerDto.ConversationDto();
        dto.setPartnerId(partner.getUserID());
        String displayName = partner.getUserName();
        if (settings != null && settings.getNickname() != null && !settings.getNickname().trim().isEmpty()) {
            displayName = settings.getNickname().trim();
        }
        dto.setPartnerName(displayName);
        dto.setPartnerAvatar(generateAvatar(partner.getUserName()));
        
        String preview = lastMsg.getContent();
        if (lastMsg.getType() == MessengerMessage.MessageType.IMAGE) preview = "Đã gửi 1 ảnh";
        if (lastMsg.getType() == MessengerMessage.MessageType.FILE) preview = "Đã gửi 1 tệp đính kèm";
        if (lastMsg.getType() == MessengerMessage.MessageType.AUDIO) preview = "Đã gửi 1 tin nhắn thoại";
        if (lastMsg.getType() == MessengerMessage.MessageType.VIDEO) preview = "Đã gửi 1 video";
        if (lastMsg.getType() == MessengerMessage.MessageType.STICKER) preview = "Đã gửi 1 nhãn dán";
        if (lastMsg.getType() == MessengerMessage.MessageType.CALL_END) {
            boolean isVideo = "VIDEO".equalsIgnoreCase(lastMsg.getMediaUrl()) || (lastMsg.getContent() != null && lastMsg.getContent().toLowerCase().contains("video"));
            if (lastMsg.getCallStatus() == MessengerMessage.CallStatus.MISSED) {
                preview = isVideo ? "Cuộc gọi video nhỡ" : "Cuộc gọi thoại nhỡ";
            } else if (lastMsg.getCallStatus() == MessengerMessage.CallStatus.REJECTED) {
                preview = isVideo ? "Cuộc gọi video bị từ chối" : "Cuộc gọi thoại bị từ chối";
            } else {
                preview = isVideo ? "Cuộc gọi video" : "Cuộc gọi thoại";
            }
        }
        
        dto.setLastMessage(preview);
        dto.setLastMessageTime(lastMsg.getTimestamp());
        dto.setLastMessageMine(isSender);
        dto.setTimeAgo(calculateTimeAgo(lastMsg.getTimestamp()));

        if (!isSender) {
            long unread = messengerRepository.countUnreadMessages(partner, me);
            dto.setUnreadCount(unread);
            dto.setRead(unread == 0);
            dto.setStatusClass(unread > 0 ? "unread" : "");
        } else {
            dto.setRead(true);
            dto.setStatusClass("");
        }

        boolean isFriend = friendRequestRepository.isFriend(me.getUserID(), partner.getUserID());
        dto.setFriend(isFriend);
        if (isFriend) {
            dto.setRelationStatus("FRIEND");
        } else {
            var sent = friendRequestRepository.findBySenderAndReceiver(me, partner);
            var received = friendRequestRepository.findBySenderAndReceiver(partner, me);
            if (sent.isPresent() && sent.get().getStatus() == com.example.project.model.FriendRequest.Status.PENDING) {
                dto.setRelationStatus("PENDING_SENT");
            } else if (received.isPresent() && received.get().getStatus() == com.example.project.model.FriendRequest.Status.PENDING) {
                dto.setRelationStatus("PENDING_RECEIVED");
            } else {
                dto.setRelationStatus("STRANGER");
            }
        }
        
        return dto;
    }

    // 2. Lấy Chat History
    @Transactional
    public List<MessengerDto.MessageDto> getChatHistory(Integer currentUserId, Integer partnerId) {
        User me = userRepository.findById(currentUserId).orElseThrow();
        User partner = userRepository.findById(partnerId).orElseThrow();

        messengerRepository.markMessagesAsRead(partner, me);

        List<MessengerMessage> messages = messengerRepository.findConversation(me, partner);
        return messages.stream().map(this::convertToMessageDto).collect(Collectors.toList());
    }

    // 1. Sửa hàm sendMessage
    public MessengerDto.MessageDto sendMessage(Integer senderId, MessengerDto.SendMessageRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User receiver = userRepository.findById(request.getReceiverId()).orElseThrow();

        MessengerMessage msg = new MessengerMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setContent(request.getContent());
        msg.setType(request.getType());
        msg.setMediaUrl(request.getContent()); 
        msg.setStatus(MessengerMessage.MessageStatus.SENT);

        // [MỚI] Xử lý Reply
        if (request.getReplyToId() != null) {
            MessengerMessage parent = messengerRepository.findById(request.getReplyToId()).orElse(null);
            msg.setReplyTo(parent);
        }

        if (request.getType() == MessageType.AUDIO) {
            // Lưu thêm metadata cho audio
            msg.setMetadata("{'type':'audio'}");
        }
        
        MessengerMessage saved = messengerRepository.save(msg);
        return convertToMessageDto(saved);
    }

    // 2. Thêm hàm thu hồi tin nhắn
    public void unsendMessage(Long messageId, Integer userId) {
        MessengerMessage msg = messengerRepository.findById(messageId).orElseThrow();
        if (msg.getSender().getUserID() == userId) {
            msg.setDeleted(true);
            messengerRepository.save(msg);
        }
    }

    @Transactional
    public MessengerDto.MessageDto editMessage(Long messageId, Integer userId, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }
        String trimmed = newContent.trim();
        if (trimmed.length() > 4000) {
            trimmed = trimmed.substring(0, 4000);
        }

        MessengerMessage message = messengerRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tin nhắn: " + messageId));

        if (message.getSender().getUserID() != userId) {
            throw new SecurityException("Chỉ người gửi mới có quyền chỉnh sửa tin nhắn này");
        }
        if (message.isDeleted()) {
            throw new IllegalStateException("Không thể chỉnh sửa tin nhắn đã bị thu hồi");
        }
        if (message.getType() != MessengerMessage.MessageType.TEXT) {
            throw new IllegalStateException("Chỉ có thể chỉnh sửa tin nhắn văn bản");
        }

        message.setContent(trimmed);

        // Cập nhật metadata phản ánh trạng thái đã sửa
        try {
            Map<String, Object> metaMap = new HashMap<>();
            if (message.getMetadata() != null && message.getMetadata().startsWith("{")) {
                try {
                    metaMap = objectMapper.readValue(message.getMetadata(), Map.class);
                } catch (Exception ignored) {}
            }
            metaMap.put("isEdited", true);
            metaMap.put("editedAt", LocalDateTime.now().toString());
            message.setMetadata(objectMapper.writeValueAsString(metaMap));
        } catch (Exception ignored) {}

        MessengerMessage saved = messengerRepository.save(message);
        return convertToMessageDto(saved);
    }

    // 3. Sửa hàm convertToMessageDto
    public  MessengerDto.MessageDto convertToMessageDto(MessengerMessage m) {
        String avatar = generateAvatar(m.getSender().getUserName());
        
        // [MỚI] Map tin nhắn gốc nếu có
        MessengerDto.MessageDto replyDto = null;
        if (m.getReplyTo() != null) {
            // Đệ quy nhẹ để lấy thông tin tin nhắn gốc (chỉ cần nội dung cơ bản)
            replyDto = MessengerDto.MessageDto.builder()
                    .id(m.getReplyTo().getId())
                    .content(m.getReplyTo().getContent())
                    .type(m.getReplyTo().getType())
                    .senderId(m.getReplyTo().getSender().getUserID()) // Để biết ai là người được reply
                    .build();
        }

        Map<String, Integer> reactionMap = getReactions(m.getId(), m.getMetadata());

        boolean isEdited = false;
        if (m.getMetadata() != null && (m.getMetadata().contains("\"isEdited\":true") || m.getMetadata().contains("\"isEdited\": true"))) {
            isEdited = true;
        }

        return MessengerDto.MessageDto.builder()
                .id(m.getId())
                .senderId(m.getSender().getUserID())
                .receiverId(m.getReceiver().getUserID())
                .content(m.isDeleted() ? "Tin nhắn đã bị thu hồi" : m.getContent()) // [MỚI] Check delete
                .type(m.getType())
                .mediaUrl(m.getMediaUrl())
                .status(m.getStatus())
                .timestamp(m.getTimestamp())
                .formattedTime(m.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                .senderAvatar(avatar)
                .isDeleted(m.isDeleted()) // [MỚI]
                .replyTo(replyDto)        // [MỚI]
                .isPinned(m.isPinned())
                .isEdited(isEdited)
                .reactions(reactionMap)
                .callDuration(m.getCallDuration())
                .callStatus(m.getCallStatus() != null ? m.getCallStatus().name() : null)
                .build();
    }

    private String calculateTimeAgo(LocalDateTime time) {
        if (time == null) return "";
        Duration diff = Duration.between(time, LocalDateTime.now());
        long seconds = diff.getSeconds();

        if (seconds < 60) return "Vừa xong";
        if (seconds < 3600) return (seconds / 60) + " phút";
        if (seconds < 86400) return (seconds / 3600) + " giờ";
        if (seconds < 604800) return (seconds / 86400) + " ngày";
        return time.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private String generateAvatar(String name) {
        try {
            return "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&background=random&color=fff";
        } catch (Exception e) { return "/images/placeholder-user.jpg"; }
    }

    // [MỚI] Lấy danh sách Media shared
    public List<MessengerDto.MessageDto> getSharedMedia(Integer currentUserId, Integer partnerId) {
        List<MessengerMessage> media = messengerRepository.findSharedMedia(currentUserId, partnerId);
        return media.stream().map(this::convertToMessageDto).collect(Collectors.toList());
    }

    // Reaction in-memory store + DB metadata persistence
    private final Map<Long, Map<String, Integer>> messageReactions = new java.util.concurrent.ConcurrentHashMap<>();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public Map<String, Integer> addOrToggleReaction(Long messageId, Integer userId, String emoji) {
        MessengerMessage message = messengerRepository.findById(messageId).orElse(null);
        Map<String, Integer> reactions = messageReactions.computeIfAbsent(messageId, k -> {
            if (message != null && message.getMetadata() != null) {
                return new java.util.concurrent.ConcurrentHashMap<>(getReactions(messageId, message.getMetadata()));
            }
            return new java.util.concurrent.ConcurrentHashMap<>();
        });
        reactions.merge(emoji, 1, Integer::sum);

        if (message != null) {
            try {
                Map<String, Object> metaMap = new HashMap<>();
                if (message.getMetadata() != null && message.getMetadata().startsWith("{")) {
                    try {
                        metaMap = objectMapper.readValue(message.getMetadata(), Map.class);
                    } catch (Exception ignored) {}
                }
                metaMap.put("reactions", new HashMap<>(reactions));
                message.setMetadata(objectMapper.writeValueAsString(metaMap));
                messengerRepository.save(message);
            } catch (Exception ignored) {}
        }

        return new java.util.HashMap<>(reactions);
    }

    public Map<String, Integer> getReactions(Long messageId) {
        return getReactions(messageId, null);
    }

    public Map<String, Integer> getReactions(Long messageId, String metadata) {
        if (messageId != null && messageReactions.containsKey(messageId)) {
            return messageReactions.get(messageId);
        }
        if (metadata != null && metadata.startsWith("{")) {
            try {
                Map<String, Object> map = objectMapper.readValue(metadata, Map.class);
                if (map.containsKey("reactions") && map.get("reactions") instanceof Map) {
                    Map<String, Integer> rMap = new HashMap<>();
                    ((Map<?, ?>) map.get("reactions")).forEach((k, v) -> {
                        if (k != null && v instanceof Number) {
                            rMap.put(k.toString(), ((Number) v).intValue());
                        }
                    });
                    if (messageId != null) {
                        messageReactions.put(messageId, rMap);
                    }
                    return rMap;
                }
            } catch (Exception ignored) {}
        }
        return java.util.Collections.emptyMap();
    }
}