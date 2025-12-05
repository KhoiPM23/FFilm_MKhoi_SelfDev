package com.example.project.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fasterxml.jackson.annotation.JsonIgnore;

    @Entity
    @Table(name = "Users")
    public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int userID;

    @NotBlank(message = "userName is not null")
    private String userName;

    @NotBlank(message = "email is not null")
    @Column(unique = true)
    private String email;

    @NotBlank(message = "password is not null")
    private String password;

    @NotBlank(message = "role is not null")
    private String role;

    private boolean status;

    @NotBlank(message = "phoneNumber is not null")
    private String phoneNumber;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Comment> comments;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Report> reports;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Subscription> subscriptions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Payment> payments;

    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // để tránh lỗi đệ quy khi serialize JSON
    private List<WatchHistory> watchHistories = new ArrayList<>();

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PrePersist
    public void prepareForPersist() {
        if (this.role == null || this.role.trim().isEmpty()) {
            this.role = "USER";
        }
        this.status = true;

        if (this.password != null && !this.password.startsWith("$2a$")) {
            this.password = encoder.encode(this.password);
        }
    }

    @PreUpdate
    public void prepareForUpdate() {
        if (this.password != null && !this.password.startsWith("$2a$")) {
            this.password = encoder.encode(this.password);
        }
    }
    
    public User() {
    }


    public User(int userID, String userName, String email, String password, String role, boolean status,
            String phoneNumber, List<Comment> comments, List<Review> reviews, List<Report> reports,
            List<Subscription> subscriptions, List<Payment> payments) {
        this.userID = userID;
        this.userName = userName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = status;
        this.phoneNumber = phoneNumber;
        this.comments = comments;
        this.reviews = reviews;
        this.reports = reports;
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    public int getUserID() {
        return userID;
    }

    public void setUserID(int userID) {
        this.userID = userID;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(List<Review> reviews) {
        this.reviews = reviews;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<Subscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    public List<WatchHistory> getWatchHistories() {
        return watchHistories;
    }

    public void setWatchHistories(List<WatchHistory> watchHistories) {
        this.watchHistories = watchHistories;
    }

    @Column(columnDefinition = "bit default 1")
    private Boolean isPublicFriendList = true; // Mặc định true

    @Column(columnDefinition = "bit default 1")
    private Boolean isPublicWatchHistory = true;

    @Column(columnDefinition = "bit default 1")
    private Boolean isPublicFavorites = true;

    // --- Getters & Setters (Xử lý Null an toàn) ---

    public boolean isPublicFriendList() {
        // Nếu database trả về null (do user cũ), mặc định coi là true (công khai)
        return isPublicFriendList != null ? isPublicFriendList : true;
    }

    public void setPublicFriendList(boolean publicFriendList) {
        this.isPublicFriendList = publicFriendList;
    }

    public boolean isPublicWatchHistory() {
        return isPublicWatchHistory != null ? isPublicWatchHistory : true;
    }

    public void setPublicWatchHistory(boolean publicWatchHistory) {
        this.isPublicWatchHistory = publicWatchHistory;
    }

    public boolean isPublicFavorites() {
        return isPublicFavorites != null ? isPublicFavorites : true;
    }

    public void setPublicFavorites(boolean publicFavorites) {
        this.isPublicFavorites = publicFavorites;
    }
}
