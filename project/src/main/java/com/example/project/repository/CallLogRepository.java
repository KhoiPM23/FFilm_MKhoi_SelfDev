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
    
    // ============= FIX 1: Thêm phương thức tìm cuộc gọi video =============
    @Query("SELECT cl FROM CallLog cl WHERE cl.userId = :userId AND cl.isVideo = true ORDER BY cl.timestamp DESC")
    List<CallLog> findVideoCalls(@Param("userId") Integer userId);
    
    // ============= FIX 2: Thêm phương thức thống kê cuộc gọi =============
    @Query("SELECT new map(" +
           "FUNCTION('DATE', cl.timestamp) as callDate, " +
           "COUNT(cl) as totalCalls, " +
           "SUM(cl.duration) as totalDuration, " +
           "AVG(cl.duration) as avgDuration) " +
           "FROM CallLog cl " +
           "WHERE cl.userId = :userId AND cl.timestamp >= :startDate " +
           "GROUP BY FUNCTION('DATE', cl.timestamp) " +
           "ORDER BY callDate DESC")
    List<Object[]> getCallStatistics(@Param("userId") Integer userId, 
                                     @Param("startDate") LocalDateTime startDate);
    
    // ============= FIX 3: Thêm phương thức tìm cuộc gọi bị lỡ =============
    @Query("SELECT cl FROM CallLog cl WHERE " +
           "(cl.userId = :userId OR cl.partnerId = :userId) AND " +
           "cl.callStatus = 'MISSED' " +
           "ORDER BY cl.timestamp DESC")
    List<CallLog> findMissedCalls(@Param("userId") Integer userId);
    
    // ============= FIX 4: Thêm phương thức xóa cuộc gọi cũ =============
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM CallLog cl WHERE cl.userId = :userId AND cl.timestamp < :cutoffDate")
    void deleteOldCalls(@Param("userId") Integer userId, @Param("cutoffDate") LocalDateTime cutoffDate);
}