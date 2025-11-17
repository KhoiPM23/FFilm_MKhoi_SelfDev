package com.example.project.dto;

import java.sql.Date;

public class AddUserFavoriteRequest {
    // [SỬA] Đổi tên thành tmdbId để tránh nhầm lẫn với ID tự tăng trong DB
    private Integer tmdbId; 
    private Integer userID;
    private Date createAt;

    public AddUserFavoriteRequest() {
    }

    public AddUserFavoriteRequest(Integer tmdbId, Integer userID, Date createAt) {
        this.tmdbId = tmdbId;
        this.userID = userID;
        this.createAt = createAt;
    }

    // [SỬA] Getter này bây giờ khớp với Service
    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public Integer getUserID() {
        return userID;
    }

    public void setUserID(Integer userID) {
        this.userID = userID;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }
}