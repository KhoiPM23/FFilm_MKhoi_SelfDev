package com.example.project.service;

import com.example.project.model.Payment;
import com.example.project.model.Subscription;
import com.example.project.repository.PaymentRepository;
import com.example.project.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BillingService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    public List<Payment> getPaymentHistory(Integer userId) {
        return paymentRepository.findByUser_UserIDOrderByPaymentDateDesc(userId);
    }

    public List<Subscription> getSubscriptionHistory(Integer userId) {
        return subscriptionRepository.findByUser_UserIDOrderByStartDateDesc(userId);
    }
    
    // Hàm lưu payment (sẽ dùng trong Controller)
    public void savePayment(Payment payment) {
        paymentRepository.save(payment);
    }
}