package com.example.project.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.model.WatchHistory;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    Optional<WatchHistory> findByUserAndMovie(User user, Movie movie);

    Page<WatchHistory> findByUserOrderByLastWatchedAtDesc(User user, Pageable pageable);

    @Query("SELECT wh.movie.movieID FROM WatchHistory wh WHERE wh.user.id = :userID")
    Set<Integer> findWatchedMovieIDsByUserID(@Param("userID") Integer userID);
}