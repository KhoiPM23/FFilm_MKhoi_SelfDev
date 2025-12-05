package com.example.project.repository;

import com.example.project.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

       @Query("SELECT m FROM ChatMessage m WHERE " +
                     "(m.senderEmail = :email OR m.recipientEmail = :email) " +
                     "ORDER BY m.timestamp ASC")
       List<ChatMessage> findChatHistoryByEmail(@Param("email") String email);

       @Query(value = "SELECT TOP 1 CASE WHEN senderEmail = :userEmail THEN recipientEmail ELSE senderEmail END " +
                     "FROM chat_messages " +
                     "WHERE (senderEmail = :userEmail OR recipientEmail = :userEmail) " +
                     "AND (senderEmail != 'SYSTEM_BOT' AND recipientEmail != 'SYSTEM_BOT') " +
                     "ORDER BY timestamp DESC", nativeQuery = true)
       String findLastModeratorChattedWith(@Param("userEmail") String userEmail);

       @Query("SELECT m FROM ChatMessage m WHERE " +
                     "m.recipientEmail = :modEmail OR " +
                     "m.senderEmail = :modEmail OR " +
                     "m.recipientEmail = 'WAITING_QUEUE' " +
                     "ORDER BY m.timestamp DESC")
       List<ChatMessage> findRawMessagesForModerator(@Param("modEmail") String modEmail);

       @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.recipientEmail = 'WAITING_QUEUE'")
       long countUsersInQueue();

       @Query("SELECT COUNT(DISTINCT m.senderEmail) FROM ChatMessage m " +
                     "WHERE (m.recipientEmail = :modEmail OR m.senderEmail = :modEmail) " +
                     "AND m.timestamp > :activeTimeThreshold")
       int countActiveClientsForModerator(@Param("modEmail") String modEmail,
                     @Param("activeTimeThreshold") java.time.LocalDateTime threshold);

       @Modifying
       @Query("UPDATE ChatMessage m SET m.status = 'SEEN', m.recipientEmail = :currentMod " +
                     "WHERE m.senderEmail = :senderEmail AND m.status = 'SENT'")
       void markAllAsSeenAndClaim(@Param("senderEmail") String senderEmail,
                     @Param("currentMod") String currentMod);

       @Query("SELECT COUNT(m) FROM ChatMessage m WHERE " +
                     "m.senderEmail = :senderEmail AND " +
                     "(m.recipientEmail = :recipientEmail OR m.recipientEmail = 'WAITING_QUEUE') AND " +
                     "m.status = 'SENT'")
       long countUnreadMessages(@Param("senderEmail") String senderEmail,
                     @Param("recipientEmail") String recipientEmail);

       @Modifying
       @Query("UPDATE ChatMessage m SET m.recipientEmail = :modEmail " +
                     "WHERE m.senderEmail = :senderEmail AND m.recipientEmail = 'WAITING_QUEUE'")
       void claimMessagesFromQueue(@Param("senderEmail") String senderEmail,
                     @Param("modEmail") String modEmail);

       // Thêm vào interface
       List<ChatMessage> findBySenderEmailAndRecipientEmailOrSenderEmailAndRecipientEmailOrderByTimestampAsc(
              String sender1, String recipient1, String sender2, String recipient2
       );
}