package com.example.project.controller;

import com.example.project.dto.MessengerDto;
import com.example.project.dto.MessengerDto.MessageDto;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.MessengerMessage;
import com.example.project.model.CallLog;
import com.example.project.model.ConversationSettings;
import com.example.project.repository.CallLogRepository;
import com.example.project.repository.ConversationSettingsRepository;
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

@RestController
@RequestMapping("/api/v1/messenger")
public class MessengerApiController {

    @Autowired private MessengerService messengerService;
    @Autowired private UserService userService;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private OnlineStatusService onlineStatusService;
    @Autowired private CallLogRepository callLogRepository;
    @Autowired private ConversationSettingsRepository conversationSettingsRepository;

    // ============= FIX: SỬA LỖI PHƯƠNG THỨC HELPER =============
    private UserSessionDto getUserFromSession(HttpSession session) {
        Object sessionUser = session.getAttribute("user");
        if (sessionUser instanceof UserSessionDto) {
            return (UserSessionDto) sessionUser;
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
            conv.setOnline(isOnline);
            conv.setLastActive(lastActive);
        });
        
        return ResponseEntity.ok(messengerService.getRecentConversations(user.getId()));
    }

    // 2. API lấy lịch sử chat
    @GetMapping("/chat/{partnerId}")
    public ResponseEntity<List<MessengerDto.MessageDto>> getChatHistory(
            @PathVariable Integer partnerId,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        List<MessengerDto.ConversationDto> conversations = messengerService.getRecentConversations(user.getId());
    
        // ← THÊM logic online
        conversations.forEach(conv -> {
            boolean isOnline = onlineStatusService.isOnline(conv.getPartnerId());
            String lastActive = onlineStatusService.getLastActive(conv.getPartnerId());
            conv.setOnline(isOnline);
            conv.setLastActive(lastActive);
        });
        
        return ResponseEntity.ok(messengerService.getChatHistory(user.getId(), partnerId));
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

        // 2. Bắn Socket cho người nhận (Realtime)
        try {
            // FIX: Lấy username người nhận để gửi socket
            String receiverUsername = userService.getUserById(request.getReceiverId()).getUserName();
            
            if (receiverUsername != null) {
                // Gửi tới: /user/{username}/queue/private
                messagingTemplate.convertAndSendToUser(
                    receiverUsername, 
                    "/queue/private", 
                    sentMessage
                );
            }
            
            // 3. Bắn lại cho chính mình (để sync các tab khác)
            messagingTemplate.convertAndSendToUser(
                user.getUserName(),
                "/queue/private",
                sentMessage
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok(sentMessage);
    }

    // API Thu hồi tin nhắn
    @PostMapping("/unsend/{messageId}")
    public ResponseEntity<?> unsendMessage(@PathVariable Long messageId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();

        messengerService.unsendMessage(messageId, user.getId());
        return ResponseEntity.ok().build();
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
            
            callLogRepository.save(log);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi lưu call log");
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
            e.printStackTrace();
            return ResponseEntity.status(500).body(List.of());
        }
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

            return ResponseEntity.ok(Map.of("pinned", message.isPinned()));
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            ConversationSettings settings = conversationSettingsRepository
                    .findByUserIdAndPartnerId(user.getId(), partnerId)
                    .orElseGet(() -> {
                        // Tạo mới nếu chưa có
                        ConversationSettings newSettings = new ConversationSettings();
                        newSettings.setUserId(user.getId());
                        newSettings.setPartnerId(partnerId);
                        return conversationSettingsRepository.save(newSettings);
                    });
            
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("/settings/theme")
    public ResponseEntity<?> updateTheme(
            @RequestBody UpdateThemeRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            int updated = conversationSettingsRepository.updateThemeColor(
                    user.getId(), request.getPartnerId(), request.getThemeColor());
            
            if (updated == 0) {
                // Tạo mới nếu chưa có
                ConversationSettings settings = new ConversationSettings();
                settings.setUserId(user.getId());
                settings.setPartnerId(request.getPartnerId());
                settings.setThemeColor(request.getThemeColor());
                conversationSettingsRepository.save(settings);
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi cập nhật theme");
        }
    }
    
    @PostMapping("/settings/nickname")
    public ResponseEntity<?> updateNickname(
            @RequestBody UpdateNicknameRequest request,
            HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        try {
            int updated = conversationSettingsRepository.updateNickname(
                    user.getId(), request.getPartnerId(), request.getNickname());
            
            if (updated == 0) {
                ConversationSettings settings = new ConversationSettings();
                settings.setUserId(user.getId());
                settings.setPartnerId(request.getPartnerId());
                settings.setNickname(request.getNickname());
                conversationSettingsRepository.save(settings);
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
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
            ConversationSettings settings = conversationSettingsRepository
                    .findByUserIdAndPartnerId(user.getId(), request.getPartnerId())
                    .orElseGet(() -> {
                        ConversationSettings newSettings = new ConversationSettings();
                        newSettings.setUserId(user.getId());
                        newSettings.setPartnerId(request.getPartnerId());
                        return newSettings;
                    });
            
            settings.setNotificationEnabled(!settings.isNotificationEnabled());
            conversationSettingsRepository.save(settings);
            
            return ResponseEntity.ok(Map.of("enabled", settings.isNotificationEnabled()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi cập nhật thông báo");
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
                List<CallLog> logs = callLogRepository.findByUserIdAndPartnerIdOrderByTimestampDesc(
                    user.getId(), partnerId);
                return ResponseEntity.ok(logs);
            } else {
                List<CallLog> logs = callLogRepository.findRecentCalls(user.getId(), fromDate);
                return ResponseEntity.ok(logs);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            Long missedCount = callLogRepository.countMissedCallsSince(user.getId(), since);
            return ResponseEntity.ok(missedCount);
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "totalMessages", 0,
                "mediaCount", 0,
                "firstMessage", null
            ));
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