package com.example.project.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content; // Nội dung: "A đã follow bạn"
    private String type;    // FOLLOW, MESSAGE, SYSTEM
    private String link;    // Link khi bấm vào: /profile/1, /messenger
    private boolean isRead = false;
    private LocalDateTime timestamp = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User recipient; // Người nhận thông báo
}