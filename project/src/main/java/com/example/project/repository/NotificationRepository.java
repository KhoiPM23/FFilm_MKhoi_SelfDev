package com.example.project.repository;

import com.example.project.model.Notification;
import com.example.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByTimestampDesc(User recipient);
    long countByRecipientAndIsReadFalse(User recipient);
}