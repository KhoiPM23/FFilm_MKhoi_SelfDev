package com.example.project.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; 

import com.example.project.dto.SubscriptionPlanRegisterRequest;
import com.example.project.model.Subscription;
import com.example.project.model.SubscriptionPlan;
import com.example.project.model.User;
import com.example.project.repository.SubscriptionPlanRepository;
import com.example.project.repository.SubscriptionRepository;
import com.example.project.repository.UserRepository;

@Service
public class SubscriptionService {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    // [LOGIC CŨ] - Giữ nguyên để tham khảo hoặc fallback
    public boolean registerSubscription(SubscriptionPlanRegisterRequest req) {
        User user = userRepository.findById(req.getUserID()).orElse(null);
        if (user == null) return false;
        
        SubscriptionPlan plan = planRepository.findById(req.getPlanID()).orElse(null);
        if (plan == null) return false;
        
        Date startDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, 30);
        Date endDate = cal.getTime();

        Subscription sub = new Subscription(startDate, endDate, true, user, plan);
        subscriptionRepository.save(sub);
        return true;
    }
    
    // [LOGIC MỚI] - Đã sửa lỗi thiếu biến startDate
    @Transactional
    public Integer registerPendingSubscription(SubscriptionPlanRegisterRequest req) {
        User user = userRepository.findById(req.getUserID()).orElse(null);
        if (user == null) return null;
        
        SubscriptionPlan plan = planRepository.findById(req.getPlanID()).orElse(null);
        if (plan == null) return null;

        // 1. Kiểm tra gói ACTIVE (Nếu có thì chặn)
        if (checkActiveSubscription(req.getUserID())) {
             throw new RuntimeException("Bạn đã có gói đăng ký đang hoạt động. Vui lòng đợi gói hiện tại kết thúc.");
        }
        
        // 2. Xóa các gói PENDING cũ (để tránh lỗi duplicate result)
        // Lưu ý: Repository phải trả về List<Subscription>
        List<Subscription> pendingSubs = subscriptionRepository.findByUser_UserIDAndStatus(req.getUserID(), false);
        if (!pendingSubs.isEmpty()) {
            subscriptionRepository.deleteAll(pendingSubs);
        }
        
        // [FIX] Thêm dòng này để khởi tạo biến startDate
        Date startDate = new Date(); 
        
        // Tạo gói với status=false (PENDING)
        Subscription sub = new Subscription(
                startDate,
                startDate, // EndDate tạm thời bằng StartDate, sẽ update khi thanh toán xong
                false,     // Status = false
                user,
                plan);
                
        subscriptionRepository.save(sub);
        return sub.getSubscriptionID();
    }
    
    public boolean checkActiveSubscription(int userID) {
        // Lưu ý: Repository phải trả về List<Subscription>
        List<Subscription> subs = subscriptionRepository.findByUser_UserIDAndStatus(userID, true);
        return !subs.isEmpty(); 
    }

    // --- CÁC HÀM PAYMENT ---
    
    public Subscription getSubscriptionById(Integer subId) {
        return subscriptionRepository.findById(subId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy Subscription ID: " + subId));
    }

    @Transactional
    public Subscription activateSubscription(Integer subId) {
        Subscription sub = getSubscriptionById(subId);
        
        if (sub.isStatus()) {
             throw new RuntimeException("Gói đăng ký đã được kích hoạt trước đó.");
        }
        
        // Tính toán ngày hết hạn (30 ngày từ lúc thanh toán thành công)
        Date startDate = new Date(); // Lấy thời điểm hiện tại làm mốc kích hoạt
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, 30);
        Date endDate = cal.getTime();

        // Cập nhật thông tin
        sub.setStartDate(startDate); // Cập nhật lại ngày bắt đầu thực tế
        sub.setEndDate(endDate); 
        sub.setStatus(true);    

        subscriptionRepository.save(sub);
        return sub;
    }
}