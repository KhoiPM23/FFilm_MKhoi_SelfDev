package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

}
