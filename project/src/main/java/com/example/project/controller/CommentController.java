package com.example.project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.Comment;
import com.example.project.service.CommentService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    private Integer extractUserId(HttpSession session) {
        if (session == null) return null;
        Object userObj = session.getAttribute("user");
        if (userObj == null) userObj = session.getAttribute("admin");
        if (userObj == null) userObj = session.getAttribute("moderator");
        if (userObj == null) userObj = session.getAttribute("contentManager");

        if (userObj instanceof UserSessionDto) {
            return ((UserSessionDto) userObj).getId();
        } else if (userObj instanceof com.example.project.model.User) {
            return ((com.example.project.model.User) userObj).getUserID();
        }
        return null;
    }

    private boolean isAdmin(HttpSession session) {
        if (session == null) return false;
        Object userObj = session.getAttribute("admin");
        if (userObj == null) userObj = session.getAttribute("user");
        if (userObj instanceof UserSessionDto) {
            String role = ((UserSessionDto) userObj).getRole();
            return role != null && role.equalsIgnoreCase("ADMIN");
        } else if (userObj instanceof com.example.project.model.User) {
            String role = ((com.example.project.model.User) userObj).getRole();
            return role != null && role.equalsIgnoreCase("ADMIN");
        }
        return false;
    }

    /**
     * Lấy tất cả comments của một phim
     * GET /api/comments/movie/{movieId}
     */
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<?> getCommentsByMovie(@PathVariable int movieId) {
        try {
            List<Comment> comments = commentService.getCommentsByMovieId(movieId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", comments.size());
            response.put("comments", comments);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi lấy danh sách comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Thêm comment mới
     * POST /api/comments
     * Body: { "movieId": 1, "content": "Phim hay quá!" }
     */
    @PostMapping
    public ResponseEntity<?> addComment(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {

        try {
            Integer userId = extractUserId(session);
            if (userId == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập để bình luận");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Lấy dữ liệu từ payload
            int movieId = (Integer) payload.get("movieId");
            String content = (String) payload.get("content");
            Integer parentCommentId = payload.get("parentCommentId") != null ? (Integer) payload.get("parentCommentId") : null;

            // Validate
            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Nội dung bình luận không được để trống");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Thêm comment (hỗ trợ reply)
            Comment newComment = commentService.addComment(movieId, userId, content, parentCommentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", parentCommentId != null ? "Phản hồi bình luận thành công" : "Thêm bình luận thành công");
            response.put("comment", newComment);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi thêm comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Thả cảm xúc cho bình luận
     * POST /api/comments/{commentId}/react
     */
    @PostMapping("/{commentId}/react")
    public ResponseEntity<?> reactComment(
            @PathVariable int commentId,
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        try {
            Integer userId = extractUserId(session);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Bạn cần đăng nhập để thả cảm xúc"));
            }

            String emoji = (String) payload.get("emoji");
            Map<String, Object> reactionData = commentService.toggleReaction(commentId, userId, emoji);
            Map<String, Object> res = new HashMap<>(reactionData);
            res.put("success", true);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi thả cảm xúc: " + e.getMessage()));
        }
    }

    /**
     * Lấy dữ liệu cảm xúc của các bình luận trong phim
     * GET /api/comments/movie/{movieId}/reactions
     */
    @GetMapping("/movie/{movieId}/reactions")
    public ResponseEntity<?> getReactionsForMovie(
            @PathVariable int movieId,
            HttpSession session) {
        try {
            Integer userId = extractUserId(session);

            Map<Integer, Object> reactionMap = commentService.getMovieReactionsMap(movieId, userId);
            return ResponseEntity.ok(Map.of("success", true, "reactions", reactionMap));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Xóa comment
     * DELETE /api/comments/{commentId}
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable int commentId,
            HttpSession session) {

        try {
            Integer userId = extractUserId(session);
            if (userId == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập để xóa bình luận");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Xóa comment
            commentService.deleteComment(commentId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Xóa bình luận thành công");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi xóa comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Đếm số lượng comments
     * GET /api/comments/count/{movieId}
     */
    @GetMapping("/count/{movieId}")
    public ResponseEntity<?> countComments(@PathVariable int movieId) {
        try {
            long count = commentService.countCommentsByMovieId(movieId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi đếm comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ============== ADMIN ENDPOINTS ==============

    /**
     * Admin: Lấy tất cả comments (bao gồm cả deleted)
     * GET /api/admin/comments
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllCommentsForAdmin(HttpSession session) {
        try {
            if (!isAdmin(session)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn không có quyền truy cập");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }

            List<Comment> comments = commentService.getAllCommentsForAdmin();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", comments.size());
            response.put("comments", comments);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi lấy danh sách comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Admin: Xóa bất kỳ comment nào
     * DELETE /api/admin/comments/{commentId}
     */
    @DeleteMapping("/admin/{commentId}")
    public ResponseEntity<?> deleteCommentByAdmin(
            @PathVariable int commentId,
            HttpSession session) {
        try {
            if (!isAdmin(session)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn không có quyền xóa comment");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }

            // Xóa comment
            commentService.deleteCommentByAdmin(commentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Xóa bình luận thành công");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi khi xóa comment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    /**
     * Cập nhật comment
     * PUT /api/comments/{commentId}
     * Body: { "content": "Nội dung mới" }
     */
    @PutMapping("/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable int commentId,
            @RequestBody Map<String, String> payload,
            HttpSession session) {

        try {
            Integer userId = extractUserId(session);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Bạn cần đăng nhập để chỉnh sửa bình luận"));
            }


            // 2. Lấy nội dung mới
            String newContent = payload.get("content");
            if (newContent == null || newContent.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Nội dung không được để trống"));
            }

            // 3. Gọi Service cập nhật
            Comment updatedComment = commentService.updateComment(commentId, userId, newContent);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cập nhật thành công",
                    "comment", updatedComment
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Đã xảy ra lỗi hệ thống"));
        }
    }
}
