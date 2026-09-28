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
    // Thêm field này vào cuối class
    private Boolean isFree;        // Lọc phim miễn phí/trả phí

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getYearFrom() { return yearFrom; }
    public void setYearFrom(Integer yearFrom) { this.yearFrom = yearFrom; }

    public Integer getYearTo() { return yearTo; }
    public void setYearTo(Integer yearTo) { this.yearTo = yearTo; }

    public Float getMinRating() { return minRating; }
    public void setMinRating(Float minRating) { this.minRating = minRating; }

    public Float getMaxRating() { return maxRating; }
    public void setMaxRating(Float maxRating) { this.maxRating = maxRating; }

    public Integer getMinDuration() { return minDuration; }
    public void setMinDuration(Integer minDuration) { this.minDuration = minDuration; }

    public Integer getMaxDuration() { return maxDuration; }
    public void setMaxDuration(Integer maxDuration) { this.maxDuration = maxDuration; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
}