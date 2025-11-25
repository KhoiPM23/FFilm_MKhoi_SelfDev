package com.example.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// DTO này dùng cho Admin tạo và cập nhật gói
public class AdminPlanRequest {

    @NotBlank(message = "Tên gói không được để trống")
    private String planName;

    @NotNull(message = "Giá không được để trống")
    @Min(value = 0, message = "Giá phải là số không âm")
    private BigDecimal price;

    // @NotBlank(message = "Mô tả không được để trống")
    // private String description;

    @NotNull(message = "Trường 'nổi bật' là bắt buộc")
    private boolean isFeatured;

    @NotNull(message = "Trường 'trạng thái' là bắt buộc")
    private boolean status;

    @NotNull(message = "Thời hạn không được để trống")
    @Min(value = 1, message = "Thời hạn phải ít nhất 1 tháng") // Sửa 'ngày' thành 'tháng'
    private Integer duration;
    
    // Getters and Setters
    public String getPlanName() {
        return planName;
    }
    public void setPlanName(String planName) {
        this.planName = planName;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    // public String getDescription() {
    //     return description;
    // }
    // public void setDescription(String description) {
    //     this.description = description;
    // }
    public boolean isFeatured() {
        return isFeatured;
    }
    public void setFeatured(boolean isFeatured) {
        this.isFeatured = isFeatured;
    }
    public boolean isStatus() {
        return status;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }
    // Getter và Setter cho duration
    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}