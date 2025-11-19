package com.example.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.dto.MovieFavorite;
import com.example.project.model.Movie;
import com.example.project.model.UserFavorite;
import com.example.project.model.UserFavoriteId;

@Repository
public interface FavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {

    boolean existsByUserIDAndMovieID(Integer userID, Integer movieID);

    @Query(value = "SELECT m.movieID, m.title, m.posterPath FROM movie m " +
            "JOIN UserFavorite uf ON m.movieID = uf.movieID " +
            "WHERE uf.userID = :userID", nativeQuery = true)
    Page<MovieFavorite> findMoviesByUserID(@Param("userID") Integer userID, Pageable pageable);
}