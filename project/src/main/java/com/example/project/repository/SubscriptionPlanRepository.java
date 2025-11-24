package com.example.project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.SubscriptionPlan;
import java.util.List;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {

    boolean existsByPlanName(String planName);

    Optional<SubscriptionPlan> findByPlanName(String planName);

    @Query("SELECT sp.duration FROM SubscriptionPlan sp WHERE sp.planID = :planID")
    Integer getDurationByPlanID(@Param("planID") Integer planID);

}
