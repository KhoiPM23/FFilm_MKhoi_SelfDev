package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.model.Episode;

public interface EpisodeRepository extends JpaRepository<Episode, Integer> {

}
