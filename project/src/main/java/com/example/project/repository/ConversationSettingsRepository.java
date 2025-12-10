package com.example.project.repository;

import com.example.project.model.ConversationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSettingsRepository extends JpaRepository<ConversationSettings, Long> {
    
    Optional<ConversationSettings> findByUserIdAndPartnerId(Integer userId, Integer partnerId);
    
    List<ConversationSettings> findByUserId(Integer userId);
    
    @Modifying
    @Query("UPDATE ConversationSettings cs SET cs.themeColor = :themeColor WHERE cs.userId = :userId AND cs.partnerId = :partnerId")
    int updateThemeColor(@Param("userId") Integer userId, 
                        @Param("partnerId") Integer partnerId, 
                        @Param("themeColor") String themeColor);
    
    @Modifying
    @Query("UPDATE ConversationSettings cs SET cs.nickname = :nickname WHERE cs.userId = :userId AND cs.partnerId = :partnerId")
    int updateNickname(@Param("userId") Integer userId, 
                      @Param("partnerId") Integer partnerId, 
                      @Param("nickname") String nickname);
    
    @Modifying
    @Query("UPDATE ConversationSettings cs SET cs.notificationEnabled = :enabled WHERE cs.userId = :userId AND cs.partnerId = :partnerId")
    int updateNotificationSetting(@Param("userId") Integer userId, 
                                 @Param("partnerId") Integer partnerId, 
                                 @Param("enabled") boolean enabled);
    
    @Query("SELECT cs FROM ConversationSettings cs WHERE cs.userId = :userId AND cs.partnerId IN :partnerIds")
    List<ConversationSettings> findByUserIdAndPartnerIdIn(@Param("userId") Integer userId, 
                                                         @Param("partnerIds") List<Integer> partnerIds);
    
    // ============= FIX 1: Thêm phương thức cập nhật background =============
    @Modifying
    @Query("UPDATE ConversationSettings cs SET cs.customBackgroundUrl = :backgroundUrl WHERE cs.userId = :userId AND cs.partnerId = :partnerId")
    int updateBackground(@Param("userId") Integer userId, 
                        @Param("partnerId") Integer partnerId, 
                        @Param("backgroundUrl") String backgroundUrl);
    
    // ============= FIX 2: Thêm phương thức mute/unmute =============
    @Modifying
    @Query("UPDATE ConversationSettings cs SET cs.mutedUntil = :mutedUntil WHERE cs.userId = :userId AND cs.partnerId = :partnerId")
    int updateMuteSetting(@Param("userId") Integer userId, 
                         @Param("partnerId") Integer partnerId, 
                         @Param("mutedUntil") java.time.LocalDateTime mutedUntil);
    
    // ============= FIX 3: Thêm phương thức lấy settings hàng loạt =============
    @Query("SELECT cs FROM ConversationSettings cs WHERE cs.userId = :userId AND cs.notificationEnabled = false")
    List<ConversationSettings> findMutedConversations(@Param("userId") Integer userId);
    
    // ============= FIX 4: Thêm phương thức kiểm tra mute =============
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
           "FROM ConversationSettings cs WHERE " +
           "cs.userId = :userId AND cs.partnerId = :partnerId AND " +
           "(cs.notificationEnabled = false OR cs.mutedUntil > CURRENT_TIMESTAMP)")
    boolean isConversationMuted(@Param("userId") Integer userId, 
                               @Param("partnerId") Integer partnerId);
}