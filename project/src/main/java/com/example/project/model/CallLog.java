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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Integer getPartnerId() { return partnerId; }
    public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public CallType getCallType() { return callType; }
    public void setCallType(CallType callType) { this.callType = callType; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public CallStatus getCallStatus() { return callStatus; }
    public void setCallStatus(CallStatus callStatus) { this.callStatus = callStatus; }

    public boolean isVideo() { return isVideo; }
    public void setVideo(boolean isVideo) { this.isVideo = isVideo; }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public Integer getInitiatorId() { return initiatorId; }
    public void setInitiatorId(Integer initiatorId) { this.initiatorId = initiatorId; }
}