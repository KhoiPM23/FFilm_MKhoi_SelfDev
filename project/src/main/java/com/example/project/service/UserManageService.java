package com.example.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.dto.UserManageDTO;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;

@Service
public class UserManageService {
   @Autowired
   private UserRepository userRepository;

   public List<UserManageDTO> getAllUsers() {
      return userRepository.findAll().stream().map(UserManageDTO :: new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getAllUsers(Pageable pageable) {
      return userRepository.findAll(pageable).map(UserManageDTO :: new);
   }

   public List<UserManageDTO> getUserByRole(String role) {
      return userRepository.findByRole(role).stream().map(UserManageDTO :: new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getUserByRole(String role, Pageable pageable) {
      return userRepository.findByRole(role, pageable).map(UserManageDTO :: new);
   }

   public Page<UserManageDTO> getUserByEmail(String email, Pageable pageable) {
      return userRepository.findByEmail(email, pageable).map(UserManageDTO :: new);
   }

   public List<UserManageDTO> getUserByStatus(boolean status) {
      return userRepository.findByStatus(status).stream().map(UserManageDTO :: new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getUserByStatus(boolean status, Pageable pageable) {
      return userRepository.findByStatus(status, pageable).map(UserManageDTO :: new);
   }

   public UserManageDTO createUser(User user) {
      user.setEmail(user.getEmail().toLowerCase().trim());
      user.setPassword(user.getPassword().trim());
      if (user.getRole() != null) {
         user.setRole(user.getRole().toLowerCase().trim());
      }
      if (userRepository.findByEmail(user.getEmail()).isPresent()) {
         throw new IllegalArgumentException("Email already exists");
      }
      if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
         throw new IllegalArgumentException("Password is required");
      }
      if (user.getPassword().length() < 6) {
         throw new IllegalArgumentException("Password must be have length >= 6");
      }

      User saved = userRepository.save(user);
      return new UserManageDTO(saved);
   }

   public void deleteUser(int id) {
      if (!userRepository.existsById(id)) {
         throw new IllegalArgumentException("User not found");
      }
      userRepository.deleteById(id);
   }

      public UserManageDTO updateUser(int id, User updateUser) {
         Optional<User> existing = userRepository.findById(id);
         if (existing.isEmpty()) {
            throw new IllegalArgumentException("User not found");
         }
         User user = existing.get();
         user.setUserName(updateUser.getUserName());
         user.setEmail(updateUser.getEmail());
         if (updateUser.getPassword() != null && !updateUser.getPassword().isEmpty()) {
            user.setPassword(updateUser.getPassword());
         }
         user.setRole(updateUser.getRole());
         user.setStatus(updateUser.isStatus());
         user.setPhoneNumber(updateUser.getPhoneNumber());
         User saved = userRepository.save(user);
         return new UserManageDTO(saved);
      }

}
