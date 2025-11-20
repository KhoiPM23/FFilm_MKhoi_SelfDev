package com.example.project.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.Subscription;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    List<Subscription> findByUser_UserIDAndStatus(Integer userId, boolean status);
    List<Subscription> findByUser_UserIDOrderByStartDateDesc(Integer userId);
}
