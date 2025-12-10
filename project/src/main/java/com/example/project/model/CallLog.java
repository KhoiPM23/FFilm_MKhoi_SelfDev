package com.example.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    
    @Column(name = "partner_id", nullable = false)
    private Integer partnerId;
    
    @Column(name = "partner_name", nullable = false, length = 100)
    private String partnerName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false)
    private CallType callType;
    
    @Column(name = "duration", nullable = false)
    private Integer duration; // seconds
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "call_status", nullable = false)
    private CallStatus callStatus;
    
    @Column(name = "is_video", nullable = false)
    private boolean isVideo = false;
    
    @Column(name = "peer_id", length = 100)
    private String peerId;
    
    @Column(name = "initiator_id")
    private Integer initiatorId;
    
    public enum CallType {
        INCOMING, OUTGOING, MISSED
    }
    
    public enum CallStatus {
        COMPLETED, MISSED, REJECTED, FAILED
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}