package com.example.project.service;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public boolean registerSubscription(SubscriptionPlanRegisterRequest req) {
        User user = userRepository.findById(req.getUserID())
                .orElse(null);
        if (user == null)
            return false;
        SubscriptionPlan plan = planRepository.findById(req.getPlanID())
                .orElse(null);
        if (plan == null)
            return false;

        Date startDate = new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, 30);
        Date endDate = cal.getTime();

        Subscription sub = new Subscription(
                startDate,
                endDate,
                true,
                user,
                plan);

        subscriptionRepository.save(sub);
        return true;
    }
}
