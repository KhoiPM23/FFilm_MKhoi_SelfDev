package com.example.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.Subscription;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // Optional<Subscription> findByUser_UserIDAndStatus(Integer userId, boolean
    // status);

    List<Subscription> findByUser_UserIDAndStatus(Integer userId, boolean status);

    List<Subscription> findByUser_UserIDOrderByStartDateDesc(Integer userId);

    boolean existsByUser_UserIDAndStatus(Integer userId, boolean status);

}
