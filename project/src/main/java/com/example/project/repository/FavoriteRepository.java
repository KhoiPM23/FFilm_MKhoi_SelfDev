package com.example.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.Movie;
import com.example.project.model.UserFavorite;
import com.example.project.model.UserFavoriteId;

@Repository
public interface FavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {

    boolean existsByUserIdAndMovieId(Integer userId, Integer movieId);

    @Query(value = "SELECT m.* FROM movie m " +
            "JOIN user_favorite uf ON m.movie_id = uf.movie_id " +
            "WHERE uf.user_id = :userID", nativeQuery = true)
    Page<Movie> findMoviesByUserId(@Param("userID") Integer userID, Pageable pageable);
}