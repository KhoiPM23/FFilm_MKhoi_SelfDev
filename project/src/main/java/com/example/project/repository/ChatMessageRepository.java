package com.example.project.repository;

import com.example.project.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 1. Lịch sử chat (Dùng JPQL - Tự động map theo tên biến trong Java, không lo tên cột)
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.senderEmail = :email OR m.recipientEmail = :email) " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findChatHistoryByEmail(@Param("email") String email);

    // 2. Tìm Moderator cũ (Native Query - Đã sửa tên cột khớp với ảnh Database của bạn)
    // Lưu ý: Đã đổi 'sender_email' thành 'senderEmail'
    @Query(value = "SELECT TOP 1 CASE WHEN senderEmail = :userEmail THEN recipientEmail ELSE senderEmail END " +
                   "FROM chat_messages " +
                   "WHERE (senderEmail = :userEmail OR recipientEmail = :userEmail) " +
                   "AND (senderEmail != 'SYSTEM_BOT' AND recipientEmail != 'SYSTEM_BOT') " +
                   "ORDER BY timestamp DESC", 
           nativeQuery = true)
    String findLastModeratorChattedWith(@Param("userEmail") String userEmail);

    // 3. Lấy tin nhắn thô cho Mod (Dùng JPQL cho an toàn)
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "m.recipientEmail = :modEmail OR " +
           "m.senderEmail = :modEmail OR " +
           "m.recipientEmail = 'WAITING_QUEUE' " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> findRawMessagesForModerator(@Param("modEmail") String modEmail);

    // 4. Đếm User chờ
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.recipientEmail = 'WAITING_QUEUE'")
    long countUsersInQueue();

    // 5. Đếm số khách đang active (Dùng JPQL để tránh sai tên cột)
    @Query("SELECT COUNT(DISTINCT m.senderEmail) FROM ChatMessage m " +
           "WHERE (m.recipientEmail = :modEmail OR m.senderEmail = :modEmail) " +
           "AND m.timestamp > :activeTimeThreshold")
    int countActiveClientsForModerator(@Param("modEmail") String modEmail, 
                                        @Param("activeTimeThreshold") java.time.LocalDateTime threshold);
}