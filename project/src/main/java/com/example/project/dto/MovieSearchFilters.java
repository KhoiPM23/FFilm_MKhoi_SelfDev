package com.example.project.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * DTO cho Advanced Search với filters
 * (Được trích xuất bởi AI từ câu nói của người dùng)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieSearchFilters {
    
    // Filters cơ bản
    private String keyword;         // Từ khóa tìm kiếm (title, description)
    private List<String> genres;    // Danh sách thể loại (tên)
    private String country;         // Quốc gia
    private String language;        // Ngôn ngữ
    
    // Filters theo thời gian
    private Integer yearFrom;       // Năm phát hành từ
    private Integer yearTo;         // Năm phát hành đến
    
    // Filters theo chất lượng
    private Float minRating;        // Rating tối thiểu
    private Float maxRating;        // Rating tối đa
    
    // Filters theo độ dài
    private Integer minDuration;    // Thời lượng tối thiểu (phút)
    private Integer maxDuration;    // Thời lượng tối đa (phút)
    
    // Filters theo người
    private String director;        // Đạo diễn
    private String actor;           // Diễn viên
    
    // Metadata (chưa dùng ở Phase 1, để dành)
    // private String sortBy;       // "rating", "releaseDate", "popularity"
    // private String sortOrder;      // "asc", "desc"
    // private Integer limit;       // Giới hạn kết quả (default: 10)
    
    /**
     * Kiểm tra có filter nào được áp dụng không
     */
    public boolean hasFilters() {
        return (keyword != null && !keyword.isEmpty()) ||
               (genres != null && !genres.isEmpty()) ||
               (country != null && !country.isEmpty()) ||
               (language != null && !language.isEmpty()) ||
               yearFrom != null ||
               yearTo != null ||
               minRating != null ||
               maxRating != null ||
               minDuration != null ||
               maxDuration != null ||
               (director != null && !director.isEmpty()) ||
               (actor != null && !actor.isEmpty());
    }
}