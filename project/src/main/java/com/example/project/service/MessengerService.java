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

                // Trong MessengerService.java -> getRecentConversations

                // Trong vòng lặp for (MessengerMessage msg : messages) hoặc for (User friend : friends)
            
                // 1. Tên: Giữ nguyên tên gốc, TUYỆT ĐỐI KHÔNG cộng chuỗi "(Người lạ)"
                dto.setPartnerName(partner.getUserName()); 
                
                // 2. Avatar: Giữ nguyên logic cũ
                dto.setPartnerAvatar(generateAvatar(partner.getUserName()));

                // 3. [FIX] Check bạn bè dùng hàm mới viết ở Repo
                boolean isFriend = friendRequestRepository.isFriend(currentUserId, partner.getUserID());
                dto.setFriend(isFriend); // Dữ liệu chuẩn: true/false

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

    // 3. Sửa hàm convertToMessageDto
    private MessengerDto.MessageDto convertToMessageDto(MessengerMessage m) {
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

        return MessengerDto.MessageDto.builder()
                .id(m.getId())
                .senderId(m.getSender().getUserID())
                .receiverId(m.getReceiver().getUserID())
                .content(m.isDeleted() ? "Tin nhắn đã bị thu hồi" : m.getContent()) // [MỚI] Check delete
                .type(m.getType())
                .status(m.getStatus())
                .timestamp(m.getTimestamp())
                .formattedTime(m.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                .senderAvatar(avatar)
                .isDeleted(m.isDeleted()) // [MỚI]
                .replyTo(replyDto)        // [MỚI]
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
}