package com.example.project.repository;

import com.example.project.model.AIChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AIChatHistoryRepository extends JpaRepository<AIChatHistory, Long> {
    // Lấy lịch sử theo User (ưu tiên)
    List<AIChatHistory> findByUserIdOrderByTimestampAsc(Integer userId);
    
    // Lấy lịch sử theo Session (cho khách)
    List<AIChatHistory> findBySessionIdOrderByTimestampAsc(String sessionId);
    
    // Lấy 10 tin nhắn gần nhất để làm context (Sort Desc rồi reverse list trong code java sau)
    List<AIChatHistory> findTop10ByUserIdOrderByTimestampDesc(Integer userId);
    List<AIChatHistory> findTop10BySessionIdOrderByTimestampDesc(String sessionId);
}