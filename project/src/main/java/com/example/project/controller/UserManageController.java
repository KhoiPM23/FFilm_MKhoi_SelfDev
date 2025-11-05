package com.example.project.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.project.dto.UserManageDTO;
import com.example.project.model.User;
import com.example.project.service.UserManageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserManageController {
    @Autowired
    private UserManageService userService;    
    
    @GetMapping
    public List<UserManageDTO> getAllUser(){
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserManageDTO> getUserById(@PathVariable int id){
        return userService.getAllUsers().stream()
                .filter(u -> u.getUserId() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/paged")
    public Page<UserManageDTO> getAllUsersPaged(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userService.getAllUsers(pageable);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody User user){
        try{
            UserManageDTO created = userService.createUser(user);
            return ResponseEntity.ok(created);
        }catch(IllegalArgumentException ex){
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable int id, @RequestBody User user){
        try{
            UserManageDTO updated = userService.updateUser(id, user);
            return ResponseEntity.ok(updated);
        }catch(IllegalArgumentException ex){
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable int id){
        try{
            userService.deleteUser(id);
            return ResponseEntity.ok().build();
        }catch(Exception ex){
            return ResponseEntity.badRequest().body("Delete failed: " + ex.getMessage());
        }
    }

    @GetMapping("/role/{role}")
    public List<UserManageDTO> getUserByRole(@PathVariable String role){
       return userService.getUserByRole(role);
    }

    @GetMapping("/status/{status}")
    public List<UserManageDTO> getUserByStatus(@PathVariable boolean status){
         return userService.getUserByStatus(status);
    }
}