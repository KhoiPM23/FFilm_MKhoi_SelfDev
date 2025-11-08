package com.example.project.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.dto.SubscriptionPlanCreateRequest;
import com.example.project.model.SubscriptionPlan;
import com.example.project.repository.SubscriptionPlanRepository;

@Service
public class SubscriptionPlanService {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    public List<SubscriptionPlan> showALlPlans() {
        return planRepository.findAll();
    }

    public boolean createSubscriptionPlan(SubscriptionPlanCreateRequest dto) {
        if (planRepository.existsByPlanName(dto.getPlanName())) {
            return false;
        }
        SubscriptionPlan subscriptionPlan = new SubscriptionPlan();
        subscriptionPlan.setPlanName(dto.getPlanName());
        subscriptionPlan.setPrice(dto.getPrice().floatValue());
        subscriptionPlan.setDescription(dto.getDescription());

        subscriptionPlan.setFeatured(false);
        subscriptionPlan.setStatus(true);
        planRepository.save(subscriptionPlan);
        return true;
    }

}
