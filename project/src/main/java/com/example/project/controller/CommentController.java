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
            // Lấy user từ session
            Object userObj = session.getAttribute("user");
            if (userObj == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập để bình luận");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Lấy userId từ UserSessionDto
            int userId;
            if (userObj instanceof UserSessionDto) {
                UserSessionDto userDto = (UserSessionDto) userObj;
                userId = userDto.getId();
            } else if (userObj instanceof com.example.project.model.User) {
                com.example.project.model.User user = (com.example.project.model.User) userObj;
                userId = user.getUserID();
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Session user không hợp lệ");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Lấy dữ liệu từ payload
            int movieId = (Integer) payload.get("movieId");
            String content = (String) payload.get("content");

            // Validate
            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Nội dung bình luận không được để trống");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Thêm comment
            Comment newComment = commentService.addComment(movieId, userId, content);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Thêm bình luận thành công");
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
     * Xóa comment
     * DELETE /api/comments/{commentId}
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable int commentId,
            HttpSession session) {

        try {
            // Kiểm tra đăng nhập
            Object userObj = session.getAttribute("user");
            if (userObj == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập để xóa bình luận");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Lấy userId từ UserSessionDto
            int userId;
            if (userObj instanceof UserSessionDto) {
                UserSessionDto userDto = (UserSessionDto) userObj;
                userId = userDto.getId();
            } else if (userObj instanceof com.example.project.model.User) {
                com.example.project.model.User user = (com.example.project.model.User) userObj;
                userId = user.getUserID();
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Session user không hợp lệ");
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
            // Kiểm tra quyền admin - thử nhiều attribute
            Object userObj = session.getAttribute("admin");
            if (userObj == null) {
                userObj = session.getAttribute("user");
            }
            
            if (userObj == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Kiểm tra role ADMIN
            String role = null;
            if (userObj instanceof UserSessionDto) {
                UserSessionDto userDto = (UserSessionDto) userObj;
                role = userDto.getRole();
            } else if (userObj instanceof com.example.project.model.User) {
                com.example.project.model.User user = (com.example.project.model.User) userObj;
                role = user.getRole();
            }

            if (role == null || !role.equalsIgnoreCase("ADMIN")) {
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
            // Kiểm tra quyền admin - thử nhiều attribute
            Object userObj = session.getAttribute("admin");
            if (userObj == null) {
                userObj = session.getAttribute("user");
            }
            
            if (userObj == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Bạn cần đăng nhập");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // Kiểm tra role ADMIN
            String role = null;
            if (userObj instanceof UserSessionDto) {
                UserSessionDto userDto = (UserSessionDto) userObj;
                role = userDto.getRole();
            } else if (userObj instanceof com.example.project.model.User) {
                com.example.project.model.User user = (com.example.project.model.User) userObj;
                role = user.getRole();
            }

            if (role == null || !role.equalsIgnoreCase("ADMIN")) {
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
}
