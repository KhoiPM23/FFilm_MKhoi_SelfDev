package com.example.project.service;

import com.example.project.dto.NotificationDto;
import com.example.project.model.Notification;
import com.example.project.model.User;
import com.example.project.repository.NotificationRepository;
import com.example.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // --- LOGIC TẠO THÔNG BÁO ---
    public void createNotification(Integer recipientId, String content, String type, String link, User sender) {
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) return;

        Notification noti = new Notification();
        noti.setRecipient(recipient); // Hoặc setRecipient tùy tên trường trong Entity của bạn
        noti.setContent(content);
        noti.setType(type);
        
        // Logic Link Profile: Nếu link null và có sender -> trỏ về profile sender
        if (link == null && sender != null) {
            noti.setLink("/social/profile/" + sender.getUserID());
        } else {
            noti.setLink(link != null ? link : "#");
        }
        
        noti.setRead(false);
        noti.setTimestamp(LocalDateTime.now());
        
        Notification saved = notificationRepository.save(noti);

        // Logic Avatar: Nếu sender có avatar -> dùng, không thì tạo từ tên
        String senderAvatar = "/images/placeholder-user.jpg";
        String senderName = "Hệ thống";
        Integer senderId = null;

        if (sender != null) {
            senderName = sender.getUserName();
            senderId = sender.getUserID();
            // Giả sử User có trường avatar, nếu không dùng hàm generate
            senderAvatar = generateAvatar(sender.getUserName());
        }

        // Tạo DTO bắn Socket
        NotificationDto dto = NotificationDto.builder()
                .id(saved.getId())
                .content(saved.getContent())
                .link(saved.getLink())
                .isRead(false)
                .type(saved.getType())
                .timeAgo("Vừa xong") // Mới tạo thì là vừa xong
                .senderAvatar(senderAvatar)
                .senderName(senderName)
                .senderId(senderId)
                .build();

        messagingTemplate.convertAndSendToUser(
            recipient.getUserName(), 
            "/queue/notifications", 
            dto
        );
    }

    // --- LOGIC LẤY DANH SÁCH (CHO API) ---
    public List<NotificationDto> getUserNotificationsDto(Integer userId) {
        User user = new User(); user.setUserID(userId);
        List<Notification> entities = notificationRepository.findByRecipientOrderByTimestampDesc(user); // Sửa lại tên hàm repository nếu cần (findByUserOrderBy...)
        
        List<NotificationDto> dtos = new ArrayList<>();
        for (Notification n : entities) {
            User sender = extractSenderFromNotification(n); // Extract từ content hoặc thêm field sender vào Entity
            dtos.add(convertToDto(n, sender));
        }
        return dtos;
    }

    public void markAllAsRead(Integer userId) {
        User user = new User(); 
        user.setUserID(userId); // Entity User dùng userID
        // Lưu ý: Check kỹ tên method trong Repo, có thể là findByUser hoặc findByRecipient
        List<Notification> list = notificationRepository.findByRecipientOrderByTimestampDesc(user); 
        
        for (Notification n : list) {
            n.setRead(true);
        }
        notificationRepository.saveAll(list); // [QUAN TRỌNG] Lưu xuống DB
    }

    // --- HELPER: CONVERT DTO & TIME AGO ---
    private NotificationDto convertToDto(Notification n, User sender) {
        String timeAgo = calculateTimeAgo(n.getTimestamp());
        String avatar = "/images/placeholder-user.jpg";
        String name = "Hệ thống";
        Integer senderId = null;

        // Logic Avatar (Nếu có sender truyền vào hoặc parse từ content/type)
        if (sender != null) {
            avatar = generateAvatar(sender.getUserName());
            name = sender.getUserName();
            senderId = sender.getUserID();
        } else if (n.getType().equals("FRIEND_REQUEST")) {
             // Logic phụ: Nếu ko có sender object, thử lấy avatar chung
             avatar = "/images/footer/logo.jpg"; 
        }

        return NotificationDto.builder()
                .id(n.getId())
                .content(n.getContent())
                .link(n.getLink())
                .isRead(n.isRead())
                .type(n.getType())
                .timeAgo(timeAgo)
                .senderAvatar(avatar)
                .senderName(name)
                .senderId(senderId)
                .build();
    }

    private String calculateTimeAgo(LocalDateTime time) {
        if (time == null) return "";
        Duration diff = Duration.between(time, LocalDateTime.now());
        long seconds = diff.getSeconds();

        if (seconds < 60) return "Vừa xong";
        if (seconds < 3600) return (seconds / 60) + " phút trước";
        if (seconds < 86400) return (seconds / 3600) + " giờ trước";
        if (seconds < 604800) return (seconds / 86400) + " ngày trước";
        return "1 tuần trước";
    }

    private String generateAvatar(String name) {
        try {
            return "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&background=random&color=fff";
        } catch (Exception e) { return "/images/placeholder-user.jpg"; }
    }

    private User extractSenderFromNotification(Notification n) {
        // Logic parse từ link hoặc content
        if (n.getType().equals("FRIEND_REQUEST") && n.getLink() != null) {
            String[] parts = n.getLink().split("/");
            Integer senderId = Integer.parseInt(parts[parts.length - 1]);
            return userRepository.findById(senderId).orElse(null);
        }
        return null;
    }
}