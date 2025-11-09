package com.example.project.repository;

import com.example.project.model.PasswordResetToken;
import com.example.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; 
import org.springframework.data.jpa.repository.Query; 
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional; // Sử dụng jakarta

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    // Phương thức deleteByUser gốc của Spring Data JPA
    void deleteByUser(User user); 

    // PHƯƠNG THỨC MỚI: Bắt buộc DELETE được thực thi ngay lập tức
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = ?1")
    void deleteAllByUser(User user); // Sử dụng tên hàm khác để tránh trùng lặp nếu cần
}