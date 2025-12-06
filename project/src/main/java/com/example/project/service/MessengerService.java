package com.example.project.service;

import com.example.project.dto.MessengerDto;
import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import com.example.project.repository.MessengerRepository;
import com.example.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessengerService {

    @Autowired private MessengerRepository messengerRepository;
    @Autowired private UserRepository userRepository;

    // 1. Lấy danh sách hội thoại (Recent Conversations) - Logic khó nhất
    public List<MessengerDto.ConversationDto> getRecentConversations(Integer currentUserId) {
        // Lấy tất cả tin nhắn liên quan
        List<MessengerMessage> allMessages = messengerRepository.findAllMessagesByUser(currentUserId);

        Map<Integer, MessengerDto.ConversationDto> conversationMap = new LinkedHashMap<>();

        for (MessengerMessage msg : allMessages) {
            User partner = msg.getSender().getUserID() == currentUserId ? msg.getReceiver() : msg.getSender();
            
            // Chỉ lấy tin nhắn mới nhất cho mỗi partner
            if (!conversationMap.containsKey(partner.getUserID())) {
                MessengerDto.ConversationDto dto = new MessengerDto.ConversationDto();
                dto.setPartnerId(partner.getUserID());
                dto.setPartnerName(partner.getUserName());
                dto.setPartnerAvatar(generateAvatar(partner.getUserName()));
                dto.setLastMessage(msg.getType() == MessengerMessage.MessageType.IMAGE ? "[Hình ảnh]" : msg.getContent());
                dto.setLastMessageTime(msg.getTimestamp());
                dto.setLastMessageMine(msg.getSender().getUserID() == currentUserId);
                
                // Tính số tin chưa đọc (Chỉ tính nếu mình là người nhận)
                    User me = userRepository.findById(currentUserId).orElse(null);
                    long unread = me != null ? messengerRepository.countUnreadMessages(partner, me) : 0;
                    dto.setUnreadCount(unread);
                
                // TODO: Tích hợp trạng thái Online từ WebSocketService sau này
                dto.setOnline(false); 

                conversationMap.put(partner.getUserID(), dto);
            }
        }

        return new ArrayList<>(conversationMap.values());
    }

    // 2. Lấy nội dung chat chi tiết
    @Transactional
    public List<MessengerDto.MessageDto> getChatHistory(Integer currentUserId, Integer partnerId) {
        User me = userRepository.findById(currentUserId).orElseThrow();
        User partner = userRepository.findById(partnerId).orElseThrow();

        // Đánh dấu đã đọc ngay khi load
        messengerRepository.markMessagesAsRead(partner, me);

        List<MessengerMessage> messages = messengerRepository.findConversation(me, partner);
        
        return messages.stream().map(m -> MessengerDto.MessageDto.builder()
                .id(m.getId())
                .senderId(m.getSender().getUserID())
                .receiverId(m.getReceiver().getUserID())
                .content(m.getContent())
                .mediaUrl(m.getMediaUrl())
                .type(m.getType())
                .status(m.getStatus())
                .timestamp(m.getTimestamp())
                .formattedTime(m.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                .build()).collect(Collectors.toList());
    }

    // 3. Lưu tin nhắn mới
    public MessengerDto.MessageDto sendMessage(Integer senderId, MessengerDto.SendMessageRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User receiver = userRepository.findById(request.getReceiverId()).orElseThrow();

        MessengerMessage msg = new MessengerMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setContent(request.getContent());
        msg.setType(request.getType());
        msg.setStatus(MessengerMessage.MessageStatus.SENT);
        
        MessengerMessage saved = messengerRepository.save(msg);

        return MessengerDto.MessageDto.builder()
                .id(saved.getId())
                .senderId(sender.getUserID())
                .receiverId(receiver.getUserID())
                .content(saved.getContent())
                .type(saved.getType())
                .status(saved.getStatus())
                .timestamp(saved.getTimestamp())
                .formattedTime(saved.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                .build();
    }

    // Helper: Tạo Avatar UI
    private String generateAvatar(String name) {
        try {
            return "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&background=random&color=fff";
        } catch (Exception e) {
            return "/images/placeholder-user.jpg";
        }
    }
}