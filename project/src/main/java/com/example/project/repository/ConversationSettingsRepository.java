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
}