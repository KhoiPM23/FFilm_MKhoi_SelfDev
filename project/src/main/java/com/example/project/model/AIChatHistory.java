package com.example.project.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AIChatHistory")
public class AIChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer userId;      
    private String sessionId;    

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String message;
    
    // [FIX QUAN TRỌNG] Đổi TEXT thành NVARCHAR(MAX) để tránh lỗi SQL Server
    @Column(columnDefinition = "NVARCHAR(MAX)") 
    private String metadata;

    @Enumerated(EnumType.STRING)
    private SenderRole role;

    private LocalDateTime timestamp;

    public enum SenderRole { USER, BOT }

    public AIChatHistory() {}

    public AIChatHistory(Integer userId, String sessionId, String message, SenderRole role) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.message = message;
        this.role = role;
        this.timestamp = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public SenderRole getRole() { return role; }
    public void setRole(SenderRole role) { this.role = role; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}