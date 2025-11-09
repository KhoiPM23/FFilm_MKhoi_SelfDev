package com.example.project.dto;

public class UserManageDTO {
    private int userId;
    private String userName;
    private String email;
    private String role;
    private boolean status;
    private String phoneNumber;

    public UserManageDTO() {
    }

    public UserManageDTO(com.example.project.model.User user) {
        this.userId = user.getUserID();
        this.userName = user.getUserName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.status = user.isStatus();
        this.phoneNumber = user.getPhoneNumber();
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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
     
    
    
}