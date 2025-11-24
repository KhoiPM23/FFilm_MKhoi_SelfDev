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
        // 1. Tìm người cũ
        String lastMod = chatMessageRepository.findLastModeratorChattedWith(userEmail);

        // 2. Check Sticky Session
        if (lastMod != null && !lastMod.equals("WAITING_QUEUE") && !lastMod.equals("SYSTEM_BOT")) {
            if (onlineModerators.contains(lastMod)) {
                System.out.println("🔄 [ROUTING] Sticky Session (Gặp lại người cũ): " + lastMod);
                return lastMod;
            } else {
                // [QUAN TRỌNG] Người cũ đã Offline -> Log ra và để nó trôi xuống thuật toán tìm
                // người mới
                System.out.println("⚠️ [ROUTING] Mod cũ (" + lastMod + ") đã Offline -> Tìm Mod mới...");
            }
        }

        // 3. Nếu không còn ai online -> Vào Queue
        if (onlineModerators.isEmpty()) {
            System.out.println("⚠️ [ROUTING] Không có Moderator online. Vào hàng chờ.");
            return null; // Controller sẽ gán WAITING_QUEUE
        }

        // 4. THUẬT TOÁN LEAST CONNECTIONS (Tìm người mới rảnh nhất)
        String bestMod = null;
        long minLoad = Long.MAX_VALUE; // Dùng Long cho chuẩn
        LocalDateTime activeThreshold = LocalDateTime.now().minusMinutes(30);

        for (String modEmail : onlineModerators) {
            long currentLoad = chatMessageRepository.countActiveClientsForModerator(modEmail, activeThreshold);

            // Ưu tiên người ít việc hơn
            if (currentLoad < minLoad) {
                minLoad = currentLoad;
                bestMod = modEmail;
            }
        }

        // Fallback an toàn
        if (bestMod == null && !onlineModerators.isEmpty()) {
            bestMod = onlineModerators.get(0);
        }

        System.out.println("🆕 [ROUTING] Assigned to New Mod: " + bestMod + " (Load: " + minLoad + ")");
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
        // 1. Cập nhật trạng thái SEEN (cho cả tin WAITING_QUEUE)
        chatMessageRepository.markAllAsSeenAndClaim(senderEmail, recipientEmail);
        // chatMessageRepository.claimMessagesFromQueue(senderEmail, recipientEmail);

        // 3. Gửi thông báo realtime (SEEN_ACK)
        ChatMessage seenAck = new ChatMessage();
        seenAck.setSenderEmail(recipientEmail);
        seenAck.setRecipientEmail(senderEmail);
        seenAck.setType(ChatMessage.MessageType.CHAT);
        seenAck.setContent("SEEN_ACK");
        seenAck.setStatus("SEEN");

        if (senderEmail.equals("WAITING_QUEUE"))
            return;

        messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", seenAck);
        messagingTemplate.convertAndSend("/topic/moderator/" + senderEmail, seenAck);
    }

    public long getUnreadCount(String senderEmail, String recipientEmail) {
        return chatMessageRepository.countUnreadMessages(senderEmail, recipientEmail);
    }
}