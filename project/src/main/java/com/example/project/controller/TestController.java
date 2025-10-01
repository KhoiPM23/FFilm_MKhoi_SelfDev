package com.example.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class TestController {
     @GetMapping("/manage-account")
    public String manageAccount() {
        return "AdminScreen/ManageAccount"; 
        // trỏ tới file: src/main/resources/templates/AdminScreen/ManageAccount.html
    }
}
