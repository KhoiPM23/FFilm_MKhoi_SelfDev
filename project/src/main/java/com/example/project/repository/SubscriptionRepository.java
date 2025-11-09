package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.Subscription;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

}
