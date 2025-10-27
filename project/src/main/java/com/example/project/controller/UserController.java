package com.example.project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.project.model.User;
import com.example.project.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    @Autowired
    private UserService userService;    
    
    @GetMapping
    public List<User> getAllUser(){
        return userService.getAllUsers();
    }
    
    @GetMapping("/role/{role}")
    public List<User> getUserByRole(@PathVariable String role){
       return userService.getUserByRole(role);
    }

    @GetMapping("/status/{status}")
    public List<User> getUserByStatus(@PathVariable boolean status){
         return userService.getUserByStatus(status);
    }
    
    

}
    

    


}
