package com.example.project.dto;

import lombok.Data;

@Data
public class WatchRoomDto {
    private Long id;
    private String name;
    private String hostName;
    private String hostAvatar;
    private int maxUsers;
    private int currentUsers;
    private boolean isActive;
    private boolean isPrivate;
    private String currentMovieTitle;
    private String currentPoster;
}