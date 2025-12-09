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

    // 1. Lấy lịch sử chat (Dùng Object User trực tiếp thì Hibernate tự hiểu ID)
    @Query("SELECT m FROM MessengerMessage m WHERE (m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1) ORDER BY m.timestamp ASC")
    List<MessengerMessage> findConversation(@Param("user1") User user1, @Param("user2") User user2);

    // 2. Đếm tin chưa đọc
    @Query("SELECT COUNT(m) FROM MessengerMessage m WHERE m.sender = :sender AND m.receiver = :receiver AND m.status <> 'READ'")
    long countUnreadMessages(@Param("sender") User sender, @Param("receiver") User receiver);

    // 3. [FIX] Đổi sender.id thành sender.userID cho đúng với model User
    @Query("SELECT m FROM MessengerMessage m WHERE m.sender.userID = :userId OR m.receiver.userID = :userId ORDER BY m.timestamp DESC")
    List<MessengerMessage> findAllMessagesByUser(@Param("userId") Integer userId);

    // 4. Update đã xem
    @Modifying
    @Query("UPDATE MessengerMessage m SET m.status = 'READ' WHERE m.sender = :sender AND m.receiver = :receiver AND m.status <> 'READ'")
    void markMessagesAsRead(@Param("sender") User sender, @Param("receiver") User receiver);

    // [MỚI] Lấy tất cả file media giữa 2 người (để hiển thị bên Sidebar phải)
    @Query("SELECT m FROM MessengerMessage m WHERE " +
            "((m.sender.userID = :u1 AND m.receiver.userID = :u2) OR " +
            "(m.sender.userID = :u2 AND m.receiver.userID = :u1)) AND " +
            "m.type IN ('IMAGE', 'VIDEO', 'FILE') " +
            "ORDER BY m.timestamp DESC")
    List<MessengerMessage> findSharedMedia(@Param("u1") Integer userId1, @Param("u2") Integer userId2);
}