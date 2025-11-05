// src/main/java/com/example/project/dto/UserRegisterDto.java
package com.example.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegisterDto {

    @NotBlank(message = "Tên người dùng không được để trống")
    private String userName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;
}