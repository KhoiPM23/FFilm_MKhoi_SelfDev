package com.example.project.repository;

import com.example.project.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Bổ sung import
import java.util.List; 

@Repository
public interface PersonRepository extends JpaRepository<Person, Integer> {
    // [BỔ SUNG] Thêm hàm này để đồng bộ diễn viên
    Optional<Person> findByTmdbId(Integer tmdbId);

    List<Person> findByFullNameContainingIgnoreCase(String name);
}