package com.example.project.repository;

import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessengerRepository extends JpaRepository<MessengerMessage, Long> {

    // Lấy toàn bộ lịch sử chat giữa 2 người, sắp xếp mới nhất ở cuối
    @Query("SELECT m FROM MessengerMessage m WHERE (m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1) ORDER BY m.timestamp ASC")
    List<MessengerMessage> findConversation(@Param("user1") User user1, @Param("user2") User user2);

    // Đếm số tin nhắn chưa đọc từ một người cụ thể gửi cho mình
    @Query("SELECT COUNT(m) FROM MessengerMessage m WHERE m.sender = :sender AND m.receiver = :receiver AND m.status != 'READ'")
    long countUnreadMessages(@Param("sender") User sender, @Param("receiver") User receiver);

    // [VIPRO] Query lấy danh sách tin nhắn mới nhất để xây dựng Conversation List
    // Lấy tất cả tin nhắn liên quan đến user, sắp xếp giảm dần theo thời gian
    @Query("SELECT m FROM MessengerMessage m WHERE m.sender.id = :userId OR m.receiver.id = :userId ORDER BY m.timestamp DESC")
    List<MessengerMessage> findAllMessagesByUser(@Param("userId") Integer userId);

    // Update trạng thái đã xem
    @Modifying
    @Query("UPDATE MessengerMessage m SET m.status = 'READ' WHERE m.sender = :sender AND m.receiver = :receiver AND m.status != 'READ'")
    void markMessagesAsRead(@Param("sender") User sender, @Param("receiver") User receiver);
}