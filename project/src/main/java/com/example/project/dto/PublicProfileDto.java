package com.example.project.dto;

import lombok.Data;
import java.util.List;
import java.util.Date;

@Data
public class PublicProfileDto {
    private Integer id;
    private String name;
    private String email; // Có thể ẩn nếu cần
    private String avatar;
    private String bio; // Giới thiệu bản thân (nếu có)

    // Stats
    private long followerCount;
    private long followingCount;
    private long friendCount;

    // Logic Quan hệ
    private String relationStatus; // "ME", "FRIEND", "PENDING_SENT", "PENDING_RECEIVED", "STRANGER"
    private boolean isFollowing;

    // Dữ liệu danh sách (Chỉ trả về nếu Public)
    private List<FriendDto> friends;
    private List<MovieCardDto> favoriteMovies;
    private List<MovieCardDto> recentWatchedMovies;

    // Inner DTO rút gọn cho list bạn bè
    @Data
    public static class FriendDto {
        private Integer id;
        private String name;
        private String avatar;
        private int mutualFriends; // Bạn chung (Nâng cao)
    }

    // Inner DTO cho Movie Card (Để hiển thị Hover Card)
    @Data
    public static class MovieCardDto {
        private Integer id;
        private String title;
        private String poster;
        private String backdrop;
        private String rating;
        private String year;
        private String url; // Link phim
        private String overview;
    }
}