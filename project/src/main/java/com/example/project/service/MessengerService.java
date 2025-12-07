package com.example.project.service;

import com.example.project.dto.MessengerDto;
import com.example.project.model.FriendRequest; // [MỚI] Thêm import này
import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.MessengerRepository;
import com.example.project.repository.UserRepository;
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

    // 1. Lấy danh sách hội thoại
    public List<MessengerDto.ConversationDto> getRecentConversations(Integer currentUserId) {
        Map<Integer, MessengerDto.ConversationDto> map = new LinkedHashMap<>();

        // Lấy User hiện tại
        User me = userRepository.findById(currentUserId).orElse(null);
        if (me == null) return new ArrayList<>(); 

        // BƯỚC 1: Lấy những người đã từng chat (Bao gồm cả người lạ)
        List<MessengerMessage> messages = messengerRepository.findAllMessagesByUser(currentUserId);
        for (MessengerMessage msg : messages) {
            boolean isSender = msg.getSender().getUserID() == currentUserId;
            User partner = isSender ? msg.getReceiver() : msg.getSender();
            
            if (!map.containsKey(partner.getUserID())) {
                MessengerDto.ConversationDto dto = new MessengerDto.ConversationDto();
                dto.setPartnerId(partner.getUserID());
                dto.setPartnerName(partner.getUserName());
                dto.setPartnerAvatar(generateAvatar(partner.getUserName()));
                
                String preview = msg.getContent();
                if (msg.getType() == MessengerMessage.MessageType.IMAGE) preview = "Đã gửi 1 ảnh";
                if (msg.getType() == MessengerMessage.MessageType.FILE) preview = "Đã gửi 1 tệp đính kèm";
                
                dto.setLastMessage(preview);
                dto.setLastMessageTime(msg.getTimestamp());
                dto.setLastMessageMine(isSender);
                dto.setTimeAgo(calculateTimeAgo(msg.getTimestamp()));

                if (!isSender) {
                    long unread = messengerRepository.countUnreadMessages(partner, me);
                    dto.setUnreadCount(unread);
                    dto.setRead(unread == 0);
                    dto.setStatusClass(unread > 0 ? "unread" : "");
                } else {
                    dto.setRead(true);
                    dto.setStatusClass("");
                }

                // [MỚI] Check xem có phải bạn bè không để hiển thị Badge "Người lạ"
                boolean isFriend = friendRequestRepository.isFriend(currentUserId, partner.getUserID());
                if (!isFriend) {
                    dto.setPartnerName(partner.getUserName() + " (Người lạ)");
                    // Hoặc dùng 1 field flag riêng trong DTO nếu muốn xử lý UI kỹ hơn
                }

                map.put(partner.getUserID(), dto);
            }
        }

        // BƯỚC 2: [FIX QUAN TRỌNG] Merge thêm bạn bè chưa từng chat
        // Thay vì để SQL xử lý logic chọn User (gây lỗi ClassCast), ta lấy List<FriendRequest> về Java xử lý
        List<FriendRequest> friendRequests = friendRequestRepository.findAllAcceptedByUserId(currentUserId);
        
        // Tự lọc ra User là bạn bè
        List<User> friends = new ArrayList<>();
        for (FriendRequest fr : friendRequests) {
            if (fr.getSender().getUserID() == currentUserId) {
                friends.add(fr.getReceiver()); // Mình gửi -> Bạn là Receiver
            } else {
                friends.add(fr.getSender());   // Mình nhận -> Bạn là Sender
            }
        }

        // Vòng lặp map vào danh sách chat (Giữ nguyên logic hiển thị)
        for (User friend : friends) {
            if (!map.containsKey(friend.getUserID())) {
                MessengerDto.ConversationDto dto = new MessengerDto.ConversationDto();
                dto.setPartnerId(friend.getUserID());
                dto.setPartnerName(friend.getUserName());
                dto.setPartnerAvatar(generateAvatar(friend.getUserName()));
                dto.setLastMessage("Các bạn đã là bạn bè trên FFilm");
                dto.setLastMessageTime(LocalDateTime.now());
                dto.setTimeAgo("");
                dto.setUnreadCount(0);
                dto.setRead(true);
                dto.setStatusClass("");

                boolean isFriend = friendRequestRepository.isFriend(currentUserId, friend.getUserID());
                dto.setFriend(isFriend);
                
                map.put(friend.getUserID(), dto);
            }
        }

        return new ArrayList<>(map.values());
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

    // 3. Gửi tin nhắn
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
        
        MessengerMessage saved = messengerRepository.save(msg);
        return convertToMessageDto(saved);
    }

    // Helper: Convert Entity -> DTO
    private MessengerDto.MessageDto convertToMessageDto(MessengerMessage m) {
        return MessengerDto.MessageDto.builder()
                .id(m.getId())
                .senderId(m.getSender().getUserID())
                .receiverId(m.getReceiver().getUserID())
                .content(m.getContent())
                .type(m.getType())
                .status(m.getStatus())
                .timestamp(m.getTimestamp())
                .formattedTime(m.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
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
}