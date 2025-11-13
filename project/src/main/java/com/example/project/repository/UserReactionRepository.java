package com.example.project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.model.UserReaction;

public interface UserReactionRepository extends JpaRepository<UserReaction, Integer> {
    Optional<UserReaction> findByUser_UserIDAndMovie_MovieID(Integer userID, Integer movieID);
}
