package com.example.project.dto;

import lombok.Data;
import java.util.List;
import java.util.Map; // Nhớ import Map

@Data
public class PublicProfileDto {
    private Integer id;
    private String name;
    private String email;
    private String avatar;
    private String bio;

    // Stats
    private long followerCount;
    private long followingCount;
    private long friendCount;

    // Logic Quan hệ
    private String relationStatus; 
    private boolean isFollowing;

    private List<FriendDto> friends;

    // [QUAN TRỌNG] Đổi sang List<Map> để tương thích với hover-card.html sử dụng cú pháp movie['key']
    private List<Map<String, Object>> favoriteMovies;
    private List<Map<String, Object>> recentWatchedMovies;

    @Data
    public static class FriendDto {
        private Integer id;
        private String name;
        private String avatar;
        private int mutualFriends;
    }

    // Bạn có thể xóa class MovieCardDto nếu không dùng nữa, 
    // hoặc giữ lại nếu dùng ở chỗ khác.
    
    @Data
    public static class GenreDto {
        private Long id;
        private String name;
        public GenreDto(Integer id, String name) {
            this.id = (id == null) ? null : id.longValue();
            this.name = name;
        }
        public GenreDto(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}