package com.example.project.repository;

import com.example.project.model.CallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CallLogRepository extends JpaRepository<CallLog, Long> {
    
    List<CallLog> findByUserIdOrderByTimestampDesc(Integer userId);
    
    List<CallLog> findByUserIdAndPartnerIdOrderByTimestampDesc(Integer userId, Integer partnerId);
    
    @Query("SELECT cl FROM CallLog cl WHERE cl.userId = :userId AND cl.timestamp >= :fromDate ORDER BY cl.timestamp DESC")
    List<CallLog> findRecentCalls(@Param("userId") Integer userId, @Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT COUNT(cl) FROM CallLog cl WHERE cl.userId = :userId AND cl.callStatus = 'MISSED' AND cl.timestamp >= :since")
    Long countMissedCallsSince(@Param("userId") Integer userId, @Param("since") LocalDateTime since);
}