package com.example.project.controller;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.ChatMessage;
import com.example.project.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
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

    // 1. Moderator Check-in (Online)
    @MessageMapping("/chat.moderatorJoin")
    public void registerModerator(@Payload ChatMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        UserSessionDto mod = getUser(headerAccessor);
        String modEmail = (mod != null) ? mod.getUserName() : msg.getSenderEmail();

        if (modEmail != null) {
            chatMessageService.addModerator(modEmail);
        }
    }

    // 2. User gửi tin nhắn đến hệ thống (Routing cho Moderator)
    @MessageMapping("/chat.sendMessageToModerator")
    public void sendMessageToModerator(@Payload ChatMessage chatMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        // [DEBUG 1] Kiểm tra xem tin nhắn có vào được Controller không
        System.out.println("🚀 [DEBUG 1] Controller nhận tin: " + chatMessage.getContent() + " từ: "
                + chatMessage.getSenderEmail());

        try {
            String senderName = null;
            UserSessionDto sessionUser = getUser(headerAccessor);

            if (sessionUser != null)
                senderName = sessionUser.getUserName();
            else if (chatMessage.getSenderEmail() != null)
                senderName = chatMessage.getSenderEmail();

            if (senderName == null) {
                System.err.println("❌ [ERROR] SenderName bị Null -> Hủy tin nhắn");
                return;
            }

            // [DEBUG 2] Xác nhận danh tính người gửi
            System.out.println("👤 [DEBUG 2] Sender xác định là: " + senderName);

            chatMessage.setSenderEmail(senderName);
            chatMessage.setTimestamp(LocalDateTime.now());
            chatMessage.setType(ChatMessage.MessageType.CHAT);

            // [DEBUG 3] Bắt đầu gọi Service chia bài (Nơi dễ lỗi nhất)
            System.out.println("🔄 [DEBUG 3] Đang gọi assignModeratorForUser...");
            String assignedMod = chatMessageService.assignModeratorForUser(senderName);
            System.out.println("✅ [DEBUG 4] Moderator được gán: " + assignedMod);

            if (assignedMod == null) {
                chatMessage.setRecipientEmail("WAITING_QUEUE");
                System.out.println("📥 [DEBUG 5] Lưu vào WAITING_QUEUE");

                ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
                System.out.println("💾 [DEBUG 6] Đã lưu DB thành công! ID: " + saved.getId()); // <-- Nếu thấy dòng này
                                                                                               // là DB chắc chắn có

                messagingTemplate.convertAndSendToUser(senderName, "/queue/messages", saved);
                messagingTemplate.convertAndSend("/topic/admin/queue", saved);
            } else {
                chatMessage.setRecipientEmail(assignedMod);
                System.out.println("📤 [DEBUG 5] Gửi cho Mod: " + assignedMod);

                ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
                System.out.println("💾 [DEBUG 6] Đã lưu DB thành công! ID: " + saved.getId());

                messagingTemplate.convertAndSendToUser(senderName, "/queue/messages", saved);
                messagingTemplate.convertAndSend("/topic/moderator/" + assignedMod, saved);
            }
        } catch (Exception e) {
            // [QUAN TRỌNG] In lỗi ra Console Server để đọc
            System.err.println("🔥 [CRITICAL ERROR] Lỗi khi xử lý tin nhắn:");
            e.printStackTrace();
        }
    }

    // 3. Moderator trả lời User
    @MessageMapping("/chat.replyToUser")
    public void replyToUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        UserSessionDto modSession = getUser(headerAccessor);
        String modName = (modSession != null) ? modSession.getUserName() : chatMessage.getSenderEmail();

        if (modName == null)
            return;

        chatMessage.setSenderEmail(modName);
        chatMessage.setTimestamp(LocalDateTime.now());

        ChatMessage saved = chatMessageService.saveChatMessage(chatMessage);
        messagingTemplate.convertAndSendToUser(chatMessage.getRecipientEmail(), "/queue/messages", saved);
        messagingTemplate.convertAndSend("/topic/moderator/" + modName, saved);
    }

    // 4. Thay đổi trạng thái (Bận/Rảnh) thủ công
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

    // API: Lấy lịch sử chat
    @GetMapping("/api/chat/history/{username}")
    @ResponseBody
    public List<ChatMessage> getHistory(@PathVariable String username) {
        return chatMessageService.getChatHistory(username);
    }

    // API: Lấy danh sách hội thoại cho Mod (Đã sửa lại theo yêu cầu của bạn)
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
}