package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.model.Season;

public interface SeasonRepository extends JpaRepository<Season, Integer> {

}
