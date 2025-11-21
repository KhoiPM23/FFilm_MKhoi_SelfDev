package com.example.project.repository;
import com.example.project.model.ProductionCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductionCompanyRepository extends JpaRepository<ProductionCompany, Integer> {
    Optional<ProductionCompany> findByTmdbId(Integer tmdbId);
}