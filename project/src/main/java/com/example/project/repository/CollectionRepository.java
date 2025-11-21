package com.example.project.repository;
import com.example.project.model.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, Integer> {
    Optional<Collection> findByTmdbId(Integer tmdbId);
}