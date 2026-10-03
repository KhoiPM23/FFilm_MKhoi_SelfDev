package com.example.project.repository;

import com.example.project.model.AIChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AIChatHistoryRepository extends JpaRepository<AIChatHistory, Long> {

    // ---- Authenticated user queries (by userId) ----
    List<AIChatHistory> findByUserIdOrderByTimestampAsc(Integer userId);
    List<AIChatHistory> findTop10ByUserIdOrderByTimestampDesc(Integer userId);

    // ---- Guest queries: sessionId + userId IS NULL (prevents leaking auth rows) ----
    List<AIChatHistory> findBySessionIdAndUserIdIsNullOrderByTimestampAsc(String sessionId);
    List<AIChatHistory> findTop10BySessionIdAndUserIdIsNullOrderByTimestampDesc(String sessionId);

    // ---- Delete: authenticated wipes all rows for that userId ----
    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Integer userId);

    // ---- Delete: guest only removes rows with userId IS NULL for that sessionId ----
    @org.springframework.transaction.annotation.Transactional
    void deleteBySessionIdAndUserIdIsNull(String sessionId);

    // ---- Legacy: kept for cleanup utilities only, not used in regular flow ----
    @org.springframework.transaction.annotation.Transactional
    void deleteBySessionId(String sessionId);
}