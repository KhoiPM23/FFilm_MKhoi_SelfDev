package com.example.project.repository;

import com.example.project.model.User;
import com.example.project.model.WatchRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WatchRoomRepository extends JpaRepository<WatchRoom, Long> {
    
    // Lấy danh sách phòng của user này
    List<WatchRoom> findByOwner(User owner);
    
    // QUAN TRỌNG: Hàm này đếm số phòng để giới hạn (Fix lỗi tại đây)
    long countByOwner(User owner);
    
    // Lấy danh sách phòng công khai đang chạy
    List<WatchRoom> findByIsActiveTrueAndAccessType(String accessType);
}