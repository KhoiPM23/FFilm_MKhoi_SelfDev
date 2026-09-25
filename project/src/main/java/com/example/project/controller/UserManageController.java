package com.example.project.controller;



import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.CrossOrigin;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import com.example.project.dto.UserManageDTO;

import com.example.project.dto.UserSessionDto;

import com.example.project.model.User;

import com.example.project.service.UserManageService;



import jakarta.servlet.http.HttpSession;



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

    public List<UserManageDTO> getAllUser() {

        return userService.getAllUsers();

    }



    @GetMapping("/{id}")

    public ResponseEntity<UserManageDTO> getUserById(@PathVariable int id) {

        return userService.getUserManageById(id)

                .map(ResponseEntity::ok)

                .orElse(ResponseEntity.notFound().build());

    }



    @GetMapping("/paged")

    public Page<UserManageDTO> getAllUsersPaged(@RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return userService.getStaffUsers(pageable);

    }



    @GetMapping("/role/{role}/paged")

    public Page<UserManageDTO> getUserByRolePage(@PathVariable String role,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return userService.getUserByRole(role, pageable);

    }



    @GetMapping("/status/{status}/paged")

    public Page<UserManageDTO> getUserByStatus(@PathVariable boolean status,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

            Pageable pageable = PageRequest.of(page, size);

                return userService.getUserByStatus(status,pageable);

    }



    @PostMapping

    public ResponseEntity<UserManageDTO> createUser(@RequestBody User user) {

        UserManageDTO created = userService.createUser(user);

        return ResponseEntity.ok(created);

    }



    @PutMapping("/{id}")

    public ResponseEntity<UserManageDTO> updateUser(@PathVariable int id, @RequestBody User user) {

        UserManageDTO updated = userService.updateUser(id, user);

        return ResponseEntity.ok(updated);

    }



    @DeleteMapping("/{id}")

    public ResponseEntity<Void> deleteUser(@PathVariable int id) {

        userService.deleteUser(id);

        return ResponseEntity.ok().build();

    }



    @GetMapping("/role/{role}")

    public List<UserManageDTO> getUserByRole(@PathVariable String role) {

        return userService.getUserByRole(role);

    }



    @GetMapping("/status/{status}")

    public List<UserManageDTO> getUserByStatus(@PathVariable boolean status) {

        return userService.getUserByStatus(status);

    }





}
