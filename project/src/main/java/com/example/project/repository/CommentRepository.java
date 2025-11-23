package com.example.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.project.model.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    // Lấy tất cả comments của một phim, sắp xếp theo thời gian mới nhất
    @Query("SELECT c FROM Comment c WHERE c.movie.movieID = :movieId AND c.status = 'approved' ORDER BY c.createAt DESC")
    List<Comment> findByMovieIdOrderByCreateAtDesc(@Param("movieId") int movieId);

    // Lấy tất cả comments của một phim (không lọc status)
    List<Comment> findByMovie_MovieIDOrderByCreateAtDesc(int movieID);

    // Lấy comments của một user
    List<Comment> findByUser_UserIDOrderByCreateAtDesc(int userID);

    // Đếm số lượng comments của một phim
    long countByMovie_MovieID(int movieID);
}
