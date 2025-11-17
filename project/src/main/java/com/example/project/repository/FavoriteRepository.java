package com.example.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.dto.MovieFavorite;
import com.example.project.model.UserFavorite;
import com.example.project.model.UserFavoriteId;

@Repository
public interface FavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {

    // [GIỮ NGUYÊN] Hàm kiểm tra tồn tại (sẽ dùng Internal ID sau khi Service được sửa)
    boolean existsByUserIDAndMovieID(Integer userID, Integer movieID);

    // [SỬA LỖI QUERY] Thay đổi điều kiện JOIN từ m.tmdbId sang m.movieID
    @Query(value = "SELECT m.movieID, m.title, m.posterPath,  m.tmdbId FROM Movie m " +
            "JOIN UserFavorite uf ON m.movieID = uf.movieID " + // <--- ĐÃ SỬA: JOIN bằng Internal MovieID
            "WHERE uf.userID = :userID ORDER BY uf.createAt DESC", // [BỔ SUNG] Sắp xếp theo ngày yêu thích gần nhất
            nativeQuery = true)
    Page<MovieFavorite> findMoviesByUserID(@Param("userID") Integer userID, Pageable pageable);
}