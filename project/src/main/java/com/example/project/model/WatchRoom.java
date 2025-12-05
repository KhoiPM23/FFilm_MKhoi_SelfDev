package com.example.project.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "WatchRoom")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Loại phòng: "PUBLIC", "PRIVATE"
    @Column(length = 10)
    private String accessType; 

    // Mật khẩu nếu là Private
    private String password; 

    // 10, 25, 100
    private int maxUsers; 

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Trạng thái phòng: true = đang có người xem, false = đang đóng
    private boolean isActive; 

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Transient // Đánh dấu không lưu vào Database, chỉ dùng để hiển thị UI
    @Getter @Setter
    private Integer currentMovieId;
    // ---------------------

}