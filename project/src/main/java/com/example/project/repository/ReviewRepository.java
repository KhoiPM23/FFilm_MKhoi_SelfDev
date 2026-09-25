package com.example.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    Optional<Review> findByUser_UserIDAndMovie_MovieID(int userId, int movieId);

    List<Review> findByMovie_MovieIDOrderByCreateAtDesc(int movieId);

    @Query("SELECT AVG(CAST(r.rating AS double)), COUNT(r) FROM Review r WHERE r.movie.movieID = :movieId")
    List<Object[]> getRatingStatsByMovieId(@Param("movieId") int movieId);

    void deleteByUser_UserIDAndMovie_MovieID(int userId, int movieId);

    boolean existsByUser_UserIDAndMovie_MovieID(int userId, int movieId);
}
