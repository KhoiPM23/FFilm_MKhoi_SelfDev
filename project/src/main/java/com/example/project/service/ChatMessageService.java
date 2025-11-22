package com.example.project.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.project.model.ChatMessage;
import com.example.project.repository.ChatMessageRepository;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final List<String> onlineModerators = new CopyOnWriteArrayList<>();

    public void addModerator(String email) {
        if (!onlineModerators.contains(email)) {
            onlineModerators.add(email);
            System.out.println("🟢 [ONLINE] Moderator đã tham gia: " + email);
        }

    }

    public void removeModerator(String email) {
        onlineModerators.remove(email);
        System.out.println("🔴 [OFFLINE] Moderator đã thoát: " + email);
    }

    public String assignModeratorForUser(String userEmail) {
        String lastMod = chatMessageRepository.findLastModeratorChattedWith(userEmail);

        if (lastMod != null && !lastMod.equals("WAITING_QUEUE") && !lastMod.equals("SYSTEM_BOT")) {
            if (onlineModerators.contains(lastMod)) {
                System.out.println("🔄 [ROUTING] Sticky Session (Gặp lại người cũ): " + lastMod);
                return lastMod;
            }
        }
        if (onlineModerators.isEmpty()) {
            System.out.println("⚠️ [ROUTING] Không có Moderator online. Vào hàng chờ.");
            return null;
        }

        // THUẬT TOÁN LEAST CONNECTIONS
        String bestMod = null;
        int minLoad = Integer.MAX_VALUE;
        // Định nghĩa "đang hoạt động" là có chat trong 30 phút qua
        LocalDateTime activeThreshold = LocalDateTime.now().minusMinutes(30);

        // Duyệt qua danh sách Mod đang online để tìm người rảnh nhất
        for (String modEmail : onlineModerators) {
            // Gọi Repository đếm xem ông này đang gánh bao nhiêu khách
            int currentLoad = chatMessageRepository.countActiveClientsForModerator(modEmail, activeThreshold);

            System.out.println("🔍 Check load: " + modEmail + " đang tiếp " + currentLoad + " khách.");

            if (currentLoad < minLoad) {
                minLoad = currentLoad;
                bestMod = modEmail;
            }
        }

        // Fallback: Nếu loop lỗi (hiếm), lấy người đầu tiên
        if (bestMod == null && !onlineModerators.isEmpty()) {
            bestMod = onlineModerators.get(0);
        }

        System.out.println("🆕 [ROUTING] Assigned to (Least Connections): " + bestMod + " (Load: " + minLoad + ")");
        return bestMod;
    }

    @Transactional
    public ChatMessage saveChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getTimestamp() == null)
            chatMessage.setTimestamp(LocalDateTime.now());
        if (chatMessage.getStatus() == null)
            chatMessage.setStatus("SENT");
        return chatMessageRepository.save(chatMessage);
    }

    public List<ChatMessage> getChatHistory(String email) {
        return chatMessageRepository.findChatHistoryByEmail(email);
    }

    public List<ChatMessage> getConversationListForModerator(String modEmail) {
        List<ChatMessage> allMessages = chatMessageRepository.findRawMessagesForModerator(modEmail);

        Map<String, ChatMessage> latestMessagesMap = new java.util.HashMap<>();

        for (ChatMessage msg : allMessages) {
            String partnerEmail;
            if (msg.getRecipientEmail().equals("WAITING_QUEUE")) {
                partnerEmail = msg.getSenderEmail();
            } else if (msg.getSenderEmail().equals(modEmail)) {
                partnerEmail = msg.getRecipientEmail();
            } else {
                partnerEmail = msg.getSenderEmail();
            }

            latestMessagesMap.putIfAbsent(partnerEmail, msg);
        }
        return new java.util.ArrayList<>(latestMessagesMap.values());
    }

    
    @Transactional
    public void markMessagesAsSeen(String senderEmail, String recipientEmail) {
        chatMessageRepository.updateStatusToSeen(senderEmail, recipientEmail);

        ChatMessage seenAck = new ChatMessage();
        seenAck.setSenderEmail(recipientEmail); 
        seenAck.setRecipientEmail(senderEmail);
        seenAck.setType(ChatMessage.MessageType.CHAT); 
        seenAck.setContent("SEEN_ACK");
        seenAck.setStatus("SEEN");

        // Gửi tín hiệu này qua WebSocket cho senderEmail
        // (Logic routing tương tự như lúc chat)
        if (senderEmail.equals("WAITING_QUEUE")) return; 


        messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", seenAck);
        messagingTemplate.convertAndSend("/topic/moderator/" + senderEmail, seenAck);
    }
}