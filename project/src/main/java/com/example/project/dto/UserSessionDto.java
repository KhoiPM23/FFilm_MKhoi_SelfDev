package com.example.project.dto;

public class UserSessionDto {

    private int id;
    private String userName;
    private String email;
    private String role;

    public UserSessionDto() {
    }

    public UserSessionDto(int id, String userName, String email, String role) {
        this.id = id;
        this.userName = userName;
        this.email = email;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    // Aliases to bridge with User entity and frontend expectations
    public int getUserID() {
        return id;
    }

    public void setUserID(int userID) {
        this.id = userID;
    }

    public String getName() {
        return userName;
    }

    public void setName(String name) {
        this.userName = name;
    }
}
