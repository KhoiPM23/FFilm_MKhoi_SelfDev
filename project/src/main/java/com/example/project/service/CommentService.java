package com.example.project.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.project.model.Comment;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.repository.CommentRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.UserRepository;

@Service
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MovieRepository movieRepository;

    /**
     * Lấy tất cả comments của một phim (chỉ approved, không bao gồm deleted)
     */
    public List<Comment> getCommentsByMovieId(int movieId) {
        return commentRepository.findByMovieIdOrderByCreateAtDesc(movieId);
    }

    /**
     * Thêm comment mới
     * 
     * @param movieId 
     * @param userId  
     * @param content 
     * @return 
     * @throws RuntimeException
     */
    @Transactional
    public Comment addComment(int movieId, int userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + userId));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie không tồn tại với ID: " + movieId));

        // Tạo comment mới
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUser(user);
        comment.setMovie(movie);
        comment.setCreateAt(new Date());
        comment.setStatus("approved"); 

        return commentRepository.save(comment);
    }

    /**
     * Xóa comment (soft delete bằng cách đổi status)
     */
    @Transactional
    public void deleteComment(int commentId, int userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));

        if (comment.getUser().getUserID() != userId) {
            throw new RuntimeException("Bạn không có quyền xóa comment này");
        }

        comment.setStatus("deleted");
        commentRepository.save(comment);
    }

    /**
     * Đếm số lượng comments của một phim
     */
    public long countCommentsByMovieId(int movieId) {
        return commentRepository.countByMovie_MovieID(movieId);
    }

    /**
     * Lấy comment theo ID
     */
    public Comment getCommentById(int commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));
    }

    /**
     * Admin: Lấy tất cả comments (không filter status)
     */
    public List<Comment> getAllCommentsForAdmin() {
        return commentRepository.findAll();
    }

    /**
     * Admin: Xóa comment (hard delete hoặc soft delete)
     */
    @Transactional
    public void deleteCommentByAdmin(int commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));
        
        // Soft delete: đổi status thành deleted
        comment.setStatus("deleted");
        commentRepository.save(comment);
        
        // Hoặc hard delete:
        // commentRepository.deleteById(commentId);
    }
    /**
     * Chỉnh sửa nội dung comment
     */
    @Transactional
    public Comment updateComment(int commentId, int userId, String newContent) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));

        // Kiểm tra quyền sở hữu
        if (comment.getUser().getUserID() != userId) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa comment này");
        }

        comment.setContent(newContent);
        // Có thể cập nhật thêm createAt nếu muốn hiển thị "Đã chỉnh sửa lúc..."
        // comment.setCreateAt(new Date()); 
        
        return commentRepository.save(comment);
    }
}
