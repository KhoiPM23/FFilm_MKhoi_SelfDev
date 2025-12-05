package com.example.project.controller;

import com.example.project.dto.SocketMessage;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.ChatMessage;
import com.example.project.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageService chatMessageService;

    // Helper: Lấy User từ WebSocket Session
    private UserSessionDto getUser(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null && attrs.containsKey("userSession")) {
            Object sessionObj = attrs.get("userSession");
            if (sessionObj instanceof UserSessionDto) {
                return (UserSessionDto) sessionObj;
            }
        }
        return null;
    }

    // ==========================================
    // PHẦN 1: MODERATOR CHAT (HỖ TRỢ KHÁCH HÀNG)
    // ==========================================

    @MessageMapping("/chat.moderatorJoin")
    public void registerModerator(@Payload ChatMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        UserSessionDto mod = getUser(headerAccessor);
        String modEmail = (mod != null) ? mod.getUserName() : msg.getSenderEmail();

        if (modEmail != null) {
            chatMessageService.addModerator(modEmail);
        }
    }

    @MessageMapping("/chat.sendMessageToModerator")
    public void sendMessageToModerator(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String senderName = null;
            UserSessionDto sessionUser = getUser(headerAccessor);

            if (sessionUser != null) senderName = sessionUser.getUserName();
            else if (chatMessage.getSenderEmail() != null) senderName = chatMessage.getSenderEmail();

            if (senderName == null) return;

            chatMessage.setSenderEmail(senderName);
            chatMessage.setTimestamp(LocalDateTime.now());
            chatMessage.setType(ChatMessage.MessageType.CHAT);

            String assignedMod = chatMessageService.assignModeratorForUser(senderName);

            if (assignedMod == null) {
                chatMessage.setRecipientEmail("WAITING_QUEUE");
                ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
                messagingTemplate.convertAndSendToUser(senderName, "/queue/messages", saved);
                messagingTemplate.convertAndSend("/topic/admin/queue", saved);
            } else {
                chatMessage.setRecipientEmail(assignedMod);
                ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
                messagingTemplate.convertAndSendToUser(senderName, "/queue/messages", saved);
                messagingTemplate.convertAndSend("/topic/moderator/" + assignedMod, saved);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @MessageMapping("/chat.replyToUser")
    public void replyToUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        UserSessionDto modSession = getUser(headerAccessor);
        String modName = (modSession != null) ? modSession.getUserName() : chatMessage.getSenderEmail();

        if (modName == null) return;

        chatMessage.setSenderEmail(modName);
        chatMessage.setTimestamp(LocalDateTime.now());

        ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
        messagingTemplate.convertAndSendToUser(chatMessage.getRecipientEmail(), "/queue/messages", saved);
        messagingTemplate.convertAndSend("/topic/moderator/" + modName, saved);
    }

    @MessageMapping("/chat.changeStatus")
    public void changeStatus(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String status = payload.get("status");
        UserSessionDto mod = getUser(headerAccessor);

        if (mod != null && status != null) {
            if ("BUSY".equals(status)) {
                chatMessageService.removeModerator(mod.getUserName());
            } else if ("ONLINE".equals(status)) {
                chatMessageService.addModerator(mod.getUserName());
            }
        }
    }

    // ==========================================
    // PHẦN 2: REST API (LỊCH SỬ & QUẢN LÝ)
    // ==========================================

    @GetMapping("/api/chat/history/{username}")
    @ResponseBody
    public List<ChatMessage> getHistory(@PathVariable String username) {
        return chatMessageService.getChatHistory(username);
    }

    @GetMapping("/api/chat/conversations")
    @ResponseBody
    public List<ChatMessage> getConversations(HttpSession session) {
        Object sessionObj = session.getAttribute("moderator");
        if (sessionObj instanceof UserSessionDto) {
            UserSessionDto userSession = (UserSessionDto) sessionObj;
            return chatMessageService.getConversationListForModerator(userSession.getUserName());
        }
        return List.of();
    }

    @PutMapping("/api/chat/seen/{senderEmail}")
    @ResponseBody
    public ResponseEntity<String> markAsSeen(@PathVariable String senderEmail, HttpSession session) {
        String viewerEmail = null;
        Object modSession = session.getAttribute("moderator");
        Object userSession = session.getAttribute("user");

        if (modSession instanceof UserSessionDto) {
            viewerEmail = ((UserSessionDto) modSession).getUserName();
        } else if (userSession instanceof UserSessionDto) {
            viewerEmail = ((UserSessionDto) userSession).getUserName();
        }

        if (viewerEmail == null) return ResponseEntity.status(401).body("Unauthorized");

        chatMessageService.markMessagesAsSeen(senderEmail, viewerEmail);
        return ResponseEntity.ok("Marked as seen");
    }

    @GetMapping("/api/chat/unread/{senderEmail}")
    @ResponseBody
    public ResponseEntity<Long> getUnreadCount(@PathVariable String senderEmail, HttpSession session) {
        Object sessionObj = session.getAttribute("moderator");
        if (!(sessionObj instanceof UserSessionDto)) return ResponseEntity.ok(0L);

        String modEmail = ((UserSessionDto) sessionObj).getUserName();
        long count = chatMessageService.getUnreadCount(senderEmail, modEmail);
        return ResponseEntity.ok(count);
    }

    // ==========================================
    // PHẦN 3: MESSENGER RIÊNG TƯ (1-1 SOCIAL)
    // ==========================================
    
    @MessageMapping("/chat.sendPrivate")
    public void sendPrivateMessage(@Payload SocketMessage msg) {
        // 1. Chuẩn hóa dữ liệu hiển thị (DTO)
        if(msg.getId() == null) msg.setId(java.util.UUID.randomUUID().toString());
        msg.setTimestamp(java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        // 2. LƯU VÀO DATABASE (QUAN TRỌNG: Cần convert SocketMessage -> ChatMessage Entity)
        try {
            ChatMessage entity = new ChatMessage();
            entity.setSenderEmail(msg.getSender()); // Người gửi
            entity.setRecipientEmail(msg.getReplyToId()); // Người nhận (Lấy từ trường replyToId của DTO)
            entity.setContent(msg.getContent());
            entity.setType(ChatMessage.MessageType.CHAT); // Hoặc tạo loại mới SOCIAL_CHAT nếu cần
            entity.setTimestamp(LocalDateTime.now());
            
            // Nếu SocketMessage có ảnh, lưu vào entity (cần đảm bảo ChatMessage có trường này hoặc ghép vào content)
            if ("IMAGE".equals(msg.getType()) && msg.getMediaUrl() != null) {
                entity.setContent("[IMAGE]" + msg.getMediaUrl()); // Hack nhẹ để lưu ảnh vào trường content
            }

            chatMessageService.saveChatMessage(entity);
        } catch (Exception e) {
            System.err.println("Lỗi lưu tin nhắn riêng tư: " + e.getMessage());
        }

        // 3. Gửi cho người nhận (Realtime)
        messagingTemplate.convertAndSendToUser(
            msg.getReplyToId(), // Username người nhận
            "/queue/private", 
            msg
        );
        
        // 4. Gửi lại cho người gửi (để UI cập nhật realtime không cần F5)
        messagingTemplate.convertAndSendToUser(
            msg.getSender(), 
            "/queue/private", 
            msg
        );
    }
}