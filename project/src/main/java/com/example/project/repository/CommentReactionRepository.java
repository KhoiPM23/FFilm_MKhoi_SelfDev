package com.example.project.repository;

import com.example.project.model.CommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Integer> {

    Optional<CommentReaction> findByCommentIdAndUserId(int commentId, int userId);

    List<CommentReaction> findByCommentId(int commentId);

    // Lấy tất cả reactions cho các comments thuộc một bộ phim
    @Query("SELECT cr FROM CommentReaction cr WHERE cr.commentId IN " +
           "(SELECT c.commentID FROM Comment c WHERE c.movie.movieID = :movieId AND c.status = 'approved')")
    List<CommentReaction> findByMovieId(@Param("movieId") int movieId);

    void deleteByCommentIdAndUserId(int commentId, int userId);
}
