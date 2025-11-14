package com.example.project.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
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

    public UserSessionDto() {
    }

}