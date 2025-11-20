package com.example.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.UserReaction;

@Repository
public interface UserReactionRepository extends JpaRepository<UserReaction, Integer> {
    Optional<UserReaction> findByUser_UserIDAndMovie_MovieID(Integer userID, Integer movieID);

    @Query("SELECT r FROM UserReaction r WHERE r.user.id = :userId")
    List<UserReaction> findByUserID(Integer userId);

    @Query("SELECT r.movie.movieID FROM UserReaction r WHERE r.user.id = :userId")
    Set<Integer> findLikedMovieIDsByUserID(@Param("userId") Integer userId);

    @Query("SELECT COUNT(r) FROM UserReaction r WHERE r.movie.movieID = :movieID AND r.isLike = TRUE")
    Long countLikesByMovieID(@Param("movieID") Integer movieID);

}
