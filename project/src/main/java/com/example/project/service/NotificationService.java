package com.example.project.service;

import com.example.project.model.Notification;
import com.example.project.model.User;
import com.example.project.repository.NotificationRepository;
import com.example.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // Hàm 1: Gọi tắt (Cho hệ thống, mặc định link null)
    public void createNotification(Integer recipientId, String content, String type) {
        createNotification(recipientId, content, type, null, null);
    }

    // Hàm 2: Có Link (Cho các tính năng khác)
    public void createNotification(Integer recipientId, String content, String type, String link) {
        createNotification(recipientId, content, type, link, null);
    }

    // Hàm 3: VIPRO (Full tham số, hỗ trợ Avatar Sender)
    public void createNotification(Integer recipientId, String content, String type, String link, User sender) {
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) return;

        Notification noti = new Notification();
        noti.setRecipient(recipient);
        noti.setContent(content);
        noti.setType(type);
        noti.setRead(false); // Mặc định là chưa đọc
        
        // Logic tạo Link thông minh
        if (link == null && sender != null) {
            noti.setLink("/social/profile/" + sender.getUserID());
        } else {
            noti.setLink(link != null ? link : "#");
        }
        
        notificationRepository.save(noti);

        // Chuẩn bị dữ liệu gửi Socket
        Map<String, Object> socketPayload = new HashMap<>();
        socketPayload.put("id", noti.getId());
        socketPayload.put("content", noti.getContent());
        socketPayload.put("link", noti.getLink());
        socketPayload.put("isRead", false);
        
        // Tạo Avatar người gửi
        String avatarUrl = "/images/footer/logo.jpg"; // Mặc định logo web
        if (sender != null) {
            try {
                String safeName = URLEncoder.encode(sender.getUserName(), StandardCharsets.UTF_8);
                avatarUrl = "https://ui-avatars.com/api/?name=" + safeName + "&background=random&color=fff";
            } catch (Exception e) {}
        }
        socketPayload.put("senderAvatar", avatarUrl);

        // Gửi Socket
        messagingTemplate.convertAndSendToUser(
            recipient.getUserName(), 
            "/queue/notifications", 
            socketPayload
        );
    }

    public List<Notification> getUserNotifications(Integer userId) {
        User user = new User(); user.setUserID(userId);
        return notificationRepository.findByRecipientOrderByTimestampDesc(user);
    }
    
    // Hàm đánh dấu đã đọc tất cả
    public void markAllAsRead(Integer userId) {
        User user = new User(); user.setUserID(userId);
        List<Notification> list = notificationRepository.findByRecipientOrderByTimestampDesc(user);
        for (Notification n : list) {
            n.setRead(true);
        }
        notificationRepository.saveAll(list);
    }
}