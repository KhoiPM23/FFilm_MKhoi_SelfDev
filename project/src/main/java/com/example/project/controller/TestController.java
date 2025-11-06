package com.example.project.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {
    
    /**
     * Trang này của Admin
     */
    @GetMapping("/manage-account")
    public String manageAccount() {
        // trỏ tới file: src/main/resources/templates/AdminScreen/ManageAccount.html
        return "AdminScreen/ManageAccount";
    }

    /**
     * Trang này của Content Manager
     */
    @GetMapping("/manage-movies")
    public String manageMovies() {
        // Trỏ tới thư mục mới của Content Manager
        return "ContentManagerScreen/ManageMovie";
    }
}