package com.example.project.dto;

import lombok.Data;

/**
 * Đối tượng này an toàn để lưu vào HttpSession.
 * Nó không chứa mật khẩu và không có liên kết JPA (Lazy-loading).
 * Annotation @Data sẽ tự động tạo Getters, Setters, và Constructor.
 */
@Data
public class UserSessionDto {
    private int id;
    private String userName;
    private String email;
    private String role;

    // Constructor để dễ dàng tạo
    public UserSessionDto(int id, String userName, String email, String role) {
        this.id = id;
        this.userName = userName;
        this.email = email;
        this.role = role;
    }
}