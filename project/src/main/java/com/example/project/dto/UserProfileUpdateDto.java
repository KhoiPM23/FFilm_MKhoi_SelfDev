package com.example.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// Sử dụng Lombok để gọn code (Giả định bạn đã dùng Lombok trong dự án)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDto {
    
    // ID của người dùng (cần thiết để tìm bản ghi trong DB)
    private int id; 

    @NotBlank(message = "Tên người dùng không được để trống")
    private String userName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Pattern(regexp = "^[\\w-\\.]+@gmail\\.com$", message = "Email phải thuộc tên miền @gmail.com")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^[0-9]{10}$", message = "Số điện thoại phải bao gồm đúng 10 chữ số")
    private String phoneNumber;
}