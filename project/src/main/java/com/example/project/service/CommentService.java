package com.example.project.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.project.model.Comment;
import com.example.project.model.CommentReaction;
import com.example.project.model.Movie;
import com.example.project.model.User;
import com.example.project.repository.CommentReactionRepository;
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

    @Autowired
    private CommentReactionRepository reactionRepository;

    /**
     * Lấy tất cả comments của một phim (chỉ approved, không bao gồm deleted)
     */
    public List<Comment> getCommentsByMovieId(int movieId) {
        return commentRepository.findByMovieIdOrderByCreateAtDesc(movieId);
    }

    /**
     * Thêm comment mới (hỗ trợ comment gốc hoặc reply qua parentCommentId)
     */
    @Transactional
    public Comment addComment(int movieId, int userId, String content) {
        return addComment(movieId, userId, content, null);
    }

    @Transactional
    public Comment addComment(int movieId, int userId, String content, Integer parentCommentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + userId));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie không tồn tại với ID: " + movieId));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUser(user);
        comment.setMovie(movie);
        comment.setCreateAt(new Date());
        comment.setStatus("approved");

        if (parentCommentId != null && parentCommentId > 0) {
            Comment parent = commentRepository.findById(parentCommentId).orElse(null);
            comment.setParentComment(parent);
        }

        return commentRepository.save(comment);
    }

    /**
     * Toggle reaction cho comment — lưu vào DB
     */
    @Transactional
    public Map<String, Object> toggleReaction(int commentId, int userId, String emoji) {
        Optional<CommentReaction> existing = reactionRepository.findByCommentIdAndUserId(commentId, userId);

        if (existing.isPresent()) {
            CommentReaction cur = existing.get();
            if (cur.getEmoji().equals(emoji)) {
                // Same emoji → remove
                reactionRepository.delete(cur);
            } else {
                // Different emoji → update
                cur.setEmoji(emoji);
                reactionRepository.save(cur);
            }
        } else {
            // New reaction
            reactionRepository.save(new CommentReaction(commentId, userId, emoji));
        }

        return buildReactionData(commentId, userId);
    }

    /**
     * Lấy reaction data cho một comment
     */
    public Map<String, Object> getCommentReactionData(int commentId, Integer userId) {
        return buildReactionData(commentId, userId);
    }

    /**
     * Lấy tất cả reactions của một bộ phim, trả về map commentId -> reactionData
     */
    public Map<Integer, Object> getMovieReactionsMap(int movieId, Integer userId) {
        List<CommentReaction> allReactions = reactionRepository.findByMovieId(movieId);

        // Group by commentId
        Map<Integer, List<CommentReaction>> byComment = allReactions.stream()
                .collect(Collectors.groupingBy(CommentReaction::getCommentId));

        Map<Integer, Object> result = new HashMap<>();
        byComment.forEach((cid, reactions) -> {
            Map<String, Integer> counts = new HashMap<>();
            String userReaction = null;
            for (CommentReaction r : reactions) {
                counts.merge(r.getEmoji(), 1, Integer::sum);
                if (userId != null && r.getUserId() == userId) {
                    userReaction = r.getEmoji();
                }
            }
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            Map<String, Object> data = new HashMap<>();
            data.put("reactions", counts);
            data.put("totalCount", total);
            data.put("userReaction", userReaction);
            result.put(cid, data);
        });
        return result;
    }

    private Map<String, Object> buildReactionData(int commentId, Integer userId) {
        List<CommentReaction> reactions = reactionRepository.findByCommentId(commentId);
        Map<String, Integer> counts = new HashMap<>();
        String userReaction = null;
        for (CommentReaction r : reactions) {
            counts.merge(r.getEmoji(), 1, Integer::sum);
            if (userId != null && r.getUserId() == userId) {
                userReaction = r.getEmoji();
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Object> res = new HashMap<>();
        res.put("reactions", counts);
        res.put("totalCount", total);
        res.put("userReaction", userReaction);
        return res;
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
     * Admin: Xóa comment (soft delete)
     */
    @Transactional
    public void deleteCommentByAdmin(int commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));
        comment.setStatus("deleted");
        commentRepository.save(comment);
    }

    /**
     * Chỉnh sửa nội dung comment
     */
    @Transactional
    public Comment updateComment(int commentId, int userId, String newContent) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment không tồn tại"));

        if (comment.getUser().getUserID() != userId) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa comment này");
        }

        comment.setContent(newContent);
        return commentRepository.save(comment);
    }
}
