package com.example.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_settings", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "partner_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    
    @Column(name = "partner_id", nullable = false)
    private Integer partnerId;
    
    @Column(name = "theme_color", length = 20)
    private String themeColor = "#0084ff";
    
    @Column(name = "nickname", length = 100)
    private String nickname;
    
    @Column(name = "notification_enabled")
    private boolean notificationEnabled = true;
    
    @Column(name = "custom_background_url")
    private String customBackgroundUrl;
    
    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (themeColor == null) {
            themeColor = "#0084ff";
        }
    }
}