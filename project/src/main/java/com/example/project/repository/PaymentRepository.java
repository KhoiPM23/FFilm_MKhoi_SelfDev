package com.example.project.repository;

import com.example.project.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    // Lấy danh sách thanh toán của user, sắp xếp mới nhất lên đầu
    List<Payment> findByUser_UserIDOrderByPaymentDateDesc(Integer userId);
}