// src/main/java/com/example/project/repository/MoviePersonRepository.java
package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.MoviePerson;
import com.example.project.model.MoviePersonId;

@Repository
public interface MoviePersonRepository extends JpaRepository<MoviePerson, MoviePersonId> {
}