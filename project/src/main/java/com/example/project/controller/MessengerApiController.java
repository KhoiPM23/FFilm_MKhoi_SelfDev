package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.dto.MessengerDto.MessageDto;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.MessengerMessage;
import com.example.project.model.CallLog;
import com.example.project.model.ConversationSettings;
import com.example.project.model.User;
import com.example.project.service.MessengerService;
import com.example.project.service.OnlineStatusService;
import com.example.project.service.UserService; 
import jakarta.servlet.http.HttpSession;
import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.project.service.SocialService;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/messenger")
public class MessengerApiController {

    private static final Logger log = LoggerFactory.getLogger(MessengerApiController.class);

    @Autowired private MessengerService messengerService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private OnlineStatusService onlineStatusService;
    @Autowired private SocialService socialService;
    @Autowired private FriendRequestRepository friendRequestRepository;
    @Autowired private com.example.project.repository.MessengerRepository messengerRepository;

    private UserSessionDto getUserFromSession(HttpSession session) {
        if (session == null) return null;
        Object sessionUser = session.getAttribute("user");
        if (sessionUser instanceof UserSessionDto) {
            return (UserSessionDto) sessionUser;
        }
        if (session.getAttribute("admin") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("admin");
        }
        if (session.getAttribute("contentManager") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("contentManager");
        }
        if (session.getAttribute("moderator") instanceof UserSessionDto) {
            return (UserSessionDto) session.getAttribute("moderator");
        }
        return null;
    }

    // 1. API lấy danh sách hội thoại
    @GetMapping("/conversations")
    public ResponseEntity<List<MessengerDto.ConversationDto>> getConversations(HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        List<MessengerDto.ConversationDto> conversations = messengerService.getRecentConversations(user.getId());
    
        // Add online status
        conversations.forEach(conv -> {
            boolean isOnline = onlineStatusService.isOnline(conv.getPartnerId());
            String lastActive = onlineStatusService.getLastActive(conv.getPartnerId());
            Long lastActiveTimestamp = onlineStatusService.getLastActiveMillis(conv.getPartnerId());
            conv.setOnline(isOnline);
            conv.setLastActive(lastActive);
            conv.setLastActiveTimestamp(lastActiveTimestamp);
        });
        
        return ResponseEntity.ok(conversations);
    }

    // 2. API lấy lịch sử chat
    @GetMapping("/chat/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getChatHistory(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        List<MessengerDto.MessageDto> history = messengerService.getChatHistory(user.getId(), partnerId);

        // Bắn Socket báo cho đối phương là toàn bộ tin nhắn đã được xem
        try {
            Map<String, Object> seenSignal = Map.of(
                "type", "SEEN_ALL",
                "partnerId", user.getId(),
                "seenBy", user.getId()
            );
            messagingTemplate.convertAndSendToUser(partnerId.toString(), "/queue/seen", seenSignal);
            messagingTemplate.convertAndSend("/topic/user." + partnerId + ".seen", seenSignal);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(history);
    }

    // 3. API Gửi tin nhắn (CÓ REALTIME)
    @PostMapping("/send")
    public ResponseEntity<MessengerDto.MessageDto> sendMessage(
            @RequestBody MessengerDto.SendMessageRequest request,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        // 1. Lưu vào DB
        MessengerDto.MessageDto sentMessage = messengerService.sendMessage(user.getId(), request);

        // 2. Bắn Socket cho người nhận (Realtime) - DÙNG userId và topic
        try {
            // Gửi tới: /user/{userId}/queue/private
            messagingTemplate.convertAndSendToUser(
                request.getReceiverId().toString(), 
                "/queue/private", 
                sentMessage
            );
            messagingTemplate.convertAndSend(
                "/topic/user." + request.getReceiverId() + ".private",
                sentMessage
            );
            
            // 3. Bắn lại cho chính mình (để sync các tab khác)
            messagingTemplate.convertAndSendToUser(
                String.valueOf(user.getId()),
                "/queue/private",
                sentMessage
            );
            messagingTemplate.convertAndSend(
                "/topic/user." + user.getId() + ".private",
                sentMessage
            );
            
        } catch (Exception e) {
            log.error("Failed to broadcast private message via WebSocket", e);
        }

        return ResponseEntity.ok(sentMessage);
    }

    // API Thu hồi tin nhắn
    @PostMapping("/unsend/{messageId}")
    public ResponseEntity<?> unsendMessage(@PathVariable Long messageId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        MessengerMessage msg = messengerRepository.findById(messageId).orElse(null);
        if (msg == null) return ResponseEntity.status(404).build();
        if (msg.getSender().getUserID() != user.getId()) {
            return ResponseEntity.status(403).body("Chỉ người gửi mới có quyền thu hồi tin nhắn này");
        }

        messengerService.unsendMessage(messageId, user.getId());
        
        try {
            Map<String, Object> unsendSignal = Map.of(
                "type", "UNSEND",
                "messageId", messageId,
                "senderId", user.getId()
            );
            messagingTemplate.convertAndSend("/topic/message." + messageId + ".unsend", unsendSignal);
            Integer partnerId = (msg.getSender().getUserID() == user.getId())
                ? msg.getReceiver().getUserID() : msg.getSender().getUserID();
            messagingTemplate.convertAndSend("/topic/user." + partnerId + ".private", unsendSignal);
            messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".private", unsendSignal);
            messagingTemplate.convertAndSendToUser(partnerId.toString(), "/queue/private", unsendSignal);
            messagingTemplate.convertAndSendToUser(String.valueOf(user.getId()), "/queue/private", unsendSignal);
        } catch (Exception e) {
            log.error("Failed to broadcast unsend", e);
        }

        return ResponseEntity.ok().build();
    }

    // API Chỉnh sửa tin nhắn (Owner only, TEXT only, not deleted)
    @PostMapping("/edit/{messageId}")
    public ResponseEntity<?> editMessage(
            @PathVariable Long messageId,
            @RequestBody MessengerDto.EditMessageRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nội dung tin nhắn không được để trống"));
        }

        try {
            MessengerDto.MessageDto editedMessage = messengerService.editMessage(messageId, user.getId(), request.getContent());
            Integer partnerId = (editedMessage.getSenderId().equals(user.getId()))
                    ? editedMessage.getReceiverId() : editedMessage.getSenderId();

            Map<String, Object> editSignal = Map.of(
                "type", "EDIT",
                "messageId", messageId,
                "content", editedMessage.getContent(),
                "isEdited", true
            );

            try {
                messagingTemplate.convertAndSendToUser(partnerId.toString(), "/queue/private", editSignal);
                messagingTemplate.convertAndSendToUser(String.valueOf(user.getId()), "/queue/private", editSignal);
                messagingTemplate.convertAndSend("/topic/user." + partnerId + ".private", editSignal);
                messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".private", editSignal);
            } catch (Exception e) {
                log.error("Failed to broadcast edit signal", e);
            }

            return ResponseEntity.ok(editedMessage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to edit message {}", messageId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Lỗi chỉnh sửa tin nhắn"));
        }
    }

    // API Thả cảm xúc
    @PostMapping("/reaction")
    public ResponseEntity<?> addReaction(
            @RequestParam Long messageId,
            @RequestParam String emoji,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        MessengerMessage msg = messengerRepository.findById(messageId).orElse(null);
        if (msg == null) return ResponseEntity.status(404).build();
        int userId = user.getId();
        if (msg.getSender().getUserID() != userId && msg.getReceiver().getUserID() != userId) {
            return ResponseEntity.status(403).body("Không có quyền thả cảm xúc vào cuộc trò chuyện này");
        }

        Map<String, Integer> reactions = messengerService.addOrToggleReaction(messageId, user.getId(), emoji);
        
        try {
            Map<String, Object> reactionSignal = Map.of(
                "type", "REACTION",
                "messageId", messageId,
                "reactions", reactions
            );
            Integer partnerId = (msg.getSender().getUserID() == user.getId())
                ? msg.getReceiver().getUserID() : msg.getSender().getUserID();
            messagingTemplate.convertAndSend("/topic/user." + partnerId + ".private", reactionSignal);
            messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".private", reactionSignal);
            messagingTemplate.convertAndSendToUser(partnerId.toString(), "/queue/private", reactionSignal);
            messagingTemplate.convertAndSendToUser(String.valueOf(user.getId()), "/queue/private", reactionSignal);
        } catch (Exception e) {
            log.error("Failed to broadcast reaction", e);
        }

        return ResponseEntity.ok(Map.of("success", true, "reactions", reactions));
    }

    // [MỚI] API lấy Media cho Sidebar phải
    @GetMapping("/media/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getSharedMedia(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(messengerService.getSharedMedia(user.getId(), partnerId));
    }

    // ============= FIX 1: Sửa endpoint call-log với repository =============
    @PostMapping("/call-log")
    public ResponseEntity<?> saveCallLog(@RequestBody CallLogRequest request, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            // Tạo và lưu call log
            CallLog log = new CallLog();
            log.setUserId(user.getId());
            log.setPartnerId(request.getPartnerId());
            log.setPartnerName(request.getPartnerName());
            
            // Chuyển đổi callType từ string sang enum
            if (request.getCallType().equals("OUTGOING") || request.getCallType().equals("INCOMING")) {
                log.setCallType(CallLog.CallType.valueOf(request.getCallType()));
                log.setVideo(request.getCallType().contains("VIDEO"));
            } else if (request.getCallType().equals("VIDEO") || request.getCallType().equals("AUDIO")) {
                // Xác định loại cuộc gọi dựa trên dữ liệu
                String callType = request.getCallType().equals("VIDEO") ? "OUTGOING" : "INCOMING";
                log.setCallType(CallLog.CallType.valueOf(callType));
                log.setVideo(request.getCallType().equals("VIDEO"));
            }
            
            log.setDuration(request.getDuration());
            log.setTimestamp(LocalDateTime.parse(request.getTimestamp()));
            log.setCallStatus(CallLog.CallStatus.COMPLETED);
            log.setPeerId(request.getPeerId());
            log.setInitiatorId(request.getInitiatorId());
            
            messengerService.saveCallLog(log);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save call log for user {}", user.getId(), e);
            return ResponseEntity.status(500).body("Lỗi lưu call log");
        }
    }

    @PostMapping("/call-record")
    public ResponseEntity<?> recordCallRecord(@RequestBody Map<String, Object> payload, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        Integer partnerId = parseInteger(payload.get("partnerId"));
        if (partnerId == null) return ResponseEntity.badRequest().body(Map.of("error", "partnerId required"));

        String callType = (String) payload.getOrDefault("callType", "VIDEO");
        String status = (String) payload.getOrDefault("status", "COMPLETED");
        int duration = 0;
        if (payload.get("duration") instanceof Number) {
            duration = ((Number) payload.get("duration")).intValue();
        } else if (payload.get("duration") != null) {
            try { duration = Integer.parseInt(payload.get("duration").toString()); } catch (Exception ignored) {}
        }
        String callId = (String) payload.get("callId");

        Integer initiatorId = parseInteger(payload.get("initiatorId"));
        Integer callerId = (initiatorId != null && (initiatorId.equals(user.getId()) || initiatorId.equals(partnerId)))
                ? initiatorId : user.getId();
        Integer calleeId = callerId.equals(user.getId()) ? partnerId : user.getId();

        try {
            MessengerDto.MessageDto messageDto = messengerService.recordCallMessage(
                    callerId, calleeId, callType, status, duration, callId
            );
            return ResponseEntity.ok(messageDto);
        } catch (Exception e) {
            log.error("Failed to record call message", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to record call"));
        }
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // ============= FIX 2: Sửa endpoint searchMessages =============
    @GetMapping("/search")
    public ResponseEntity<List<MessageDto>> searchMessages(
            @RequestParam Integer partnerId,
            @RequestParam String query,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            List<MessengerMessage> messages = messengerService.searchMessages(
                user.getId(), partnerId, query
            );
            
            List<MessageDto> result = messages.stream()
                .map(messengerService::convertToMessageDto)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to search messages for user {} and partner {}", user.getId(), partnerId, e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    // Endpoint tìm kiếm người dùng để bắt đầu chat mới
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> searchUsersForChat(
            @RequestParam(required = false, defaultValue = "") String q,
            HttpSession session) {
        UserSessionDto currentUser = getUserFromSession(session);
        if (currentUser == null) return ResponseEntity.status(401).build();

        String query = q.trim().toLowerCase();
        List<User> allUsers = userService.getAllUsers();
        List<Map<String, Object>> result = allUsers.stream()
                .filter(u -> u.getUserID() != currentUser.getId())
                .filter(u -> query.isEmpty()
                        || (u.getUserName() != null && u.getUserName().toLowerCase().contains(query))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(query)))
                .limit(20)
                .map(u -> {
                    String displayName = (u.getUserName() != null && !u.getUserName().isBlank()) ? u.getUserName() : u.getEmail();
                    String avatar = "https://ui-avatars.com/api/?name=" + java.net.URLEncoder.encode(displayName, java.nio.charset.StandardCharsets.UTF_8);
                    return Map.<String, Object>of(
                            "id", u.getUserID(),
                            "name", displayName,
                            "email", u.getEmail() != null ? u.getEmail() : "",
                            "avatar", avatar
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Endpoint lấy thông tin công khai an toàn của user để bắt đầu chat mới (không cần quyền Admin)
    @GetMapping("/user/{partnerId}")
    public ResponseEntity<Map<String, Object>> getUserInfoForChat(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto currentUser = getUserFromSession(session);
        if (currentUser == null) return ResponseEntity.status(401).build();

        User u = userService.getUserById(partnerId);
        if (u == null) return ResponseEntity.status(404).build();

        String displayName = (u.getUserName() != null && !u.getUserName().isBlank()) ? u.getUserName() : u.getEmail();
        String avatar;
        try {
            avatar = "https://ui-avatars.com/api/?name=" + URLEncoder.encode(displayName, StandardCharsets.UTF_8) + "&background=random&color=fff";
        } catch (Exception e) {
            avatar = "/images/placeholder-user.jpg";
        }
        boolean isFriend = friendRequestRepository.isFriend(currentUser.getId(), partnerId);
        boolean isOnline = onlineStatusService.isOnline(partnerId);
        String lastActive = onlineStatusService.getLastActive(partnerId);

        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getUserID());
        map.put("userID", u.getUserID());
        map.put("name", displayName);
        map.put("userName", displayName);
        map.put("email", u.getEmail() != null ? u.getEmail() : "");
        map.put("avatar", avatar);
        map.put("isFriend", isFriend);
        map.put("isOnline", isOnline);
        map.put("lastActive", lastActive != null ? lastActive : "");
        return ResponseEntity.ok(map);
    }
    
    // ============= FIX 3: Sửa endpoint togglePinMessage - SỬA LỖI CHÍNH =============
    @PostMapping("/pin/{messageId}")
    public ResponseEntity<?> togglePinMessage(@PathVariable Long messageId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            MessengerMessage message = messengerService.getMessageById(messageId);
            
            // FIX: Sử dụng int để so sánh, không dùng .equals() trên primitive
            int userId = user.getId();
            int senderId = message.getSender().getUserID(); // getUserID() trả về int
            int receiverId = message.getReceiver().getUserID();
            
            if (senderId != userId && receiverId != userId) {
                return ResponseEntity.status(403).build();
            }
            
            // read current value safely (may be null)
            Boolean currentPinned = message.isPinned();
            boolean newPinned = !(currentPinned != null && currentPinned.booleanValue());
            message.setIsPinned(newPinned);
            messengerService.saveMessage(message);

            // Broadcast WebSocket PIN signal đến cả 2 người
            try {
                int partnerId = (senderId == userId) ? receiverId : senderId;
                Map<String, Object> pinSignal = Map.of(
                    "type", "PIN",
                    "messageId", messageId,
                    "pinned", newPinned,
                    "isPinned", newPinned,
                    "content", message.getContent() != null ? message.getContent() : ""
                );
                messagingTemplate.convertAndSendToUser(String.valueOf(partnerId), "/queue/private", pinSignal);
                messagingTemplate.convertAndSend("/topic/user." + partnerId + ".private", pinSignal);
                messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/private", pinSignal);
                messagingTemplate.convertAndSend("/topic/user." + userId + ".private", pinSignal);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of("pinned", message.isPinned()));
        } catch (Exception e) {
            log.error("Failed to toggle pin for messageId {}", messageId, e);
            return ResponseEntity.status(404).body("Không tìm thấy tin nhắn");
        }
    }
    
    // ============= FIX 4: Sửa endpoint getPinnedMessages =============
    @GetMapping("/pinned/{partnerId}")
    public ResponseEntity<List<MessageDto>> getPinnedMessages(
            @PathVariable Integer partnerId,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            List<MessengerMessage> pinned = messengerService.getPinnedMessages(
                user.getId(), partnerId
            );
            
            List<MessageDto> result = pinned.stream()
                .map(messengerService::convertToMessageDto)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get pinned messages for user {} and partner {}", user.getId(), partnerId, e);
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    // ============= FIX 5: Thêm endpoint cho Conversation Settings =============
    @GetMapping("/settings/{partnerId}")
    public ResponseEntity<ConversationSettings> getConversationSettings(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            ConversationSettings settings = messengerService
                    .getConversationSettings(user.getId(), partnerId)
                    .orElseGet(() -> {
                        // Tạo mới nếu chưa có
                        ConversationSettings newSettings = new ConversationSettings();
                        newSettings.setUserId(user.getId());
                        newSettings.setPartnerId(partnerId);
                        return messengerService.saveConversationSettings(newSettings);
                    });
            
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Failed to get conversation settings for user {} and partner {}", user.getId(), partnerId, e);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("/settings/theme")
    public ResponseEntity<?> updateTheme(
            @RequestBody UpdateThemeRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        if (request == null || request.getPartnerId() == null || request.getPartnerId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "partnerId không hợp lệ"));
        }
        String color = request.getThemeColor();
        if (color == null || !color.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã màu không hợp lệ"));
        }
        
        try {
            int updated = messengerService.updateThemeColor(
                    user.getId(), request.getPartnerId(), color);
            
            if (updated == 0) {
                // Tạo mới nếu chưa có
                ConversationSettings settings = new ConversationSettings();
                settings.setUserId(user.getId());
                settings.setPartnerId(request.getPartnerId());
                settings.setThemeColor(color);
                messengerService.saveConversationSettings(settings);
            }
            
            // Broadcast theme change via STOMP to both participants
            try {
                Map<String, Object> themeSignal = Map.of(
                    "type", "THEME",
                    "partnerId", user.getId(),
                    "themeColor", color
                );
                messagingTemplate.convertAndSendToUser(request.getPartnerId().toString(), "/queue/private", themeSignal);
                messagingTemplate.convertAndSendToUser(String.valueOf(user.getId()), "/queue/private", themeSignal);
                messagingTemplate.convertAndSend("/topic/user." + request.getPartnerId() + ".private", themeSignal);
                messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".private", themeSignal);
            } catch (Exception ignored) {}

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update theme color for user {} and partner {}", user.getId(), request.getPartnerId(), e);
            return ResponseEntity.status(500).body("Lỗi cập nhật theme");
        }
    }
    
    @PostMapping("/settings/nickname")
    public ResponseEntity<?> updateNickname(
            @RequestBody UpdateNicknameRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        if (request == null || request.getPartnerId() == null || request.getPartnerId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "partnerId không hợp lệ"));
        }

        String rawNick = request.getNickname();
        String safeNickname = null;
        if (rawNick != null) {
            String trimmed = rawNick.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 50) trimmed = trimmed.substring(0, 50);
                safeNickname = org.springframework.web.util.HtmlUtils.htmlEscape(trimmed);
            }
        }
        
        try {
            int updated = messengerService.updateNickname(
                    user.getId(), request.getPartnerId(), safeNickname);
            
            if (updated == 0) {
                ConversationSettings settings = new ConversationSettings();
                settings.setUserId(user.getId());
                settings.setPartnerId(request.getPartnerId());
                settings.setNickname(safeNickname);
                messengerService.saveConversationSettings(settings);
            }
            
            return ResponseEntity.ok(Map.of("nickname", safeNickname != null ? safeNickname : ""));
        } catch (Exception e) {
            log.error("Failed to update nickname for user {} and partner {}", user.getId(), request.getPartnerId(), e);
            return ResponseEntity.status(500).body("Lỗi cập nhật nickname");
        }
    }
    
    @PostMapping("/settings/notification")
    public ResponseEntity<?> toggleNotification(
            @RequestBody NotificationRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            ConversationSettings settings = messengerService
                    .getConversationSettings(user.getId(), request.getPartnerId())
                    .orElseGet(() -> {
                        ConversationSettings newSettings = new ConversationSettings();
                        newSettings.setUserId(user.getId());
                        newSettings.setPartnerId(request.getPartnerId());
                        return newSettings;
                    });
            
            settings.setNotificationEnabled(!settings.isNotificationEnabled());
            messengerService.saveConversationSettings(settings);
            
            return ResponseEntity.ok(Map.of("enabled", settings.isNotificationEnabled()));
        } catch (Exception e) {
            log.error("Failed to toggle notification for user {} and partner {}", user.getId(), request.getPartnerId(), e);
            return ResponseEntity.status(500).body("Lỗi cập nhật thông báo");
        }
    }

    @PostMapping("/settings/background")
    public ResponseEntity<?> updateBackground(
            @RequestParam(required = false) Integer partnerId,
            @RequestParam(required = false) String background,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        Integer targetPartnerId = partnerId;
        String bgValue = background;
        if (targetPartnerId == null && body != null && body.containsKey("partnerId")) {
            targetPartnerId = Integer.valueOf(body.get("partnerId").toString());
        }
        if (bgValue == null && body != null && body.containsKey("background")) {
            bgValue = (String) body.get("background");
        }
        if (targetPartnerId == null) return ResponseEntity.badRequest().body("partnerId required");
        
        try {
            final Integer pId = targetPartnerId;
            ConversationSettings settings = messengerService
                    .getConversationSettings(user.getId(), targetPartnerId)
                    .orElseGet(() -> {
                        ConversationSettings newSettings = new ConversationSettings();
                        newSettings.setUserId(user.getId());
                        newSettings.setPartnerId(pId);
                        return newSettings;
                    });
            settings.setCustomBackgroundUrl(bgValue);
            messengerService.saveConversationSettings(settings);
            return ResponseEntity.ok(Map.of("success", true, "background", bgValue != null ? bgValue : ""));
        } catch (Exception e) {
            log.error("Failed to update background", e);
            return ResponseEntity.ok(Map.of("success", true));
        }
    }
    
    // ============= FIX 6: Thêm endpoint lịch sử cuộc gọi =============
    @GetMapping("/call-history")
    public ResponseEntity<List<CallLog>> getCallHistory(
            @RequestParam(required = false) Integer partnerId,
            @RequestParam(defaultValue = "30") int days,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
            
            if (partnerId != null) {
                List<CallLog> logs = messengerService.getCallLogsByPartner(
                    user.getId(), partnerId);
                return ResponseEntity.ok(logs);
            } else {
                List<CallLog> logs = messengerService.getRecentCalls(user.getId(), fromDate);
                return ResponseEntity.ok(logs);
            }
        } catch (Exception e) {
            log.error("Failed to get call history for user {}", user.getId(), e);
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    @GetMapping("/call-history/missed")
    public ResponseEntity<Long> getMissedCallsCount(
            @RequestParam(defaultValue = "7") int days,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(days);
            Long missedCount = messengerService.countMissedCallsSince(user.getId(), since);
            return ResponseEntity.ok(missedCount);
        } catch (Exception e) {
            log.error("Failed to count missed calls for user {}", user.getId(), e);
            return ResponseEntity.ok(0L);
        }
    }
    
    // ============= FIX 7: Thêm endpoint cho thống kê =============
    @GetMapping("/stats/{partnerId}")
    public ResponseEntity<Map<String, Object>> getChatStats(
            @PathVariable Integer partnerId,
            HttpSession session) {
        
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            Map<String, Object> stats = messengerService.getChatStats(user.getId(), partnerId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get chat stats for user {} and partner {}", user.getId(), partnerId, e);
            return ResponseEntity.ok(Map.of(
                "totalMessages", 0,
                "mediaCount", 0,
                "firstMessage", null
            ));
        }
    }

    // 14. API kiểm tra mối quan hệ (BẠN BÈ / NGƯỜI LẠ / ĐÃ GỬI / NHẬN)
    @GetMapping("/relation/{partnerId}")
    public ResponseEntity<Map<String, String>> getRelationStatus(@PathVariable Integer partnerId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        Integer myId = user.getId();
        if (myId.equals(partnerId)) {
            return ResponseEntity.ok(Map.of("relationStatus", "ME"));
        }

        boolean isFriend = friendRequestRepository.isFriend(myId, partnerId);
        if (isFriend) {
            return ResponseEntity.ok(Map.of("relationStatus", "FRIEND"));
        }

        User me = new User(); me.setUserID(myId);
        User partner = new User(); partner.setUserID(partnerId);
        var sent = friendRequestRepository.findBySenderAndReceiver(me, partner);
        if (sent.isPresent() && sent.get().getStatus() == com.example.project.model.FriendRequest.Status.PENDING) {
            return ResponseEntity.ok(Map.of("relationStatus", "PENDING_SENT"));
        }
        var received = friendRequestRepository.findBySenderAndReceiver(partner, me);
        if (received.isPresent() && received.get().getStatus() == com.example.project.model.FriendRequest.Status.PENDING) {
            return ResponseEntity.ok(Map.of("relationStatus", "PENDING_RECEIVED"));
        }

        return ResponseEntity.ok(Map.of("relationStatus", "STRANGER"));
    }

    // 15. API Chặn người dùng trong Messenger
    @PostMapping("/block/{partnerId}")
    public ResponseEntity<?> blockPartner(@PathVariable Integer partnerId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        if (partnerId == null || partnerId.equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể chặn chính mình"));
        }
        if (!userRepository.existsById(partnerId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Người dùng không tồn tại"));
        }

        try {
            socialService.unfriendUser(user.getId(), partnerId);
            return ResponseEntity.ok(Map.of("status", "BLOCKED", "message", "Đã chặn người dùng thành công"));
        } catch (Exception e) {
            log.error("Failed to block user {}", partnerId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Lỗi khi chặn người dùng"));
        }
    }

    // ============= FIX 8: ĐỊNH NGHĨA CÁC DTO NỘI BỘ =============
    @Data
    public static class CallLogRequest {
        private Integer partnerId;
        private String partnerName;
        private String callType;
        private Integer duration;
        private String timestamp;
        private String peerId;
        private Integer initiatorId;
    }
    
    @Data
    public static class UpdateThemeRequest {
        private Integer partnerId;
        private String themeColor;
    }
    
    @Data
    public static class UpdateNicknameRequest {
        private Integer partnerId;
        private String nickname;
    }
    
    @Data
    public static class NotificationRequest {
        private Integer partnerId;
    }
}