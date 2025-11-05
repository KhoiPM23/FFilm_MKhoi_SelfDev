// src/main/java/com/example/project/dto/UserDto.java
package com.example.project.dto;

public class UserDto {
    private String userName;
    private String email;
    private String phoneNumber;

    // CONSTRUCTOR 3 THAM SỐ
    public UserDto(String userName, String email, String phoneNumber) {
        this.userName = userName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    // GETTER
    public String getUserName() { return userName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
}