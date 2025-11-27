package com.example.project.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
      return userRepository.findAll().stream().map(UserManageDTO::new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getAllUsers(Pageable pageable) {
      return userRepository.findAll(pageable).map(UserManageDTO::new);
   }

   public Page<UserManageDTO> getStaffUsers(Pageable pageable) {
      List<String> roles = Arrays.asList("moderator", "content_manager");
      return userRepository.findByRoleIn(roles, pageable).map(UserManageDTO::new);
   }

   public List<UserManageDTO> getUserByRole(String role) {
      return userRepository.findByRole(role).stream().map(UserManageDTO::new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getUserByRole(String role, Pageable pageable) {
      return userRepository.findByRole(role, pageable).map(UserManageDTO::new);
   }

   public Page<UserManageDTO> getUserByEmail(String email, Pageable pageable) {
      return userRepository.findByEmail(email, pageable).map(UserManageDTO::new);
   }

   public List<UserManageDTO> getUserByStatus(boolean status) {
      return userRepository.findByStatus(status).stream().map(UserManageDTO::new).collect(Collectors.toList());
   }

   public Page<UserManageDTO> getUserByStatus(boolean status, Pageable pageable) {
      List<String> roles = Arrays.asList("moderator", "content_manager");
      return userRepository.findByStatusAndRoleIn(status, roles, pageable).map(UserManageDTO::new);
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
      if (user.getEmail() == null || !user.getEmail().toLowerCase().endsWith("@gmail.com")) {
         throw new IllegalArgumentException("Email must be have format @gmail.com");
      }
      if (user.getPhoneNumber().length() != 10) {
         throw new IllegalArgumentException("Phone Number must be 10 numbers");
      }
      if (userRepository.findByPhoneNumber(user.getPhoneNumber()).isPresent()) {
         throw new IllegalArgumentException("Phonenumber already exists");
      }
      if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
         throw new IllegalArgumentException("Password is required");
      }
      if (user.getPassword().length() < 6) {
         throw new IllegalArgumentException("Password must be have length >= 6");
      }
      if (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty()) {
         throw new IllegalArgumentException("PhoneNumber is required");
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
      if (!user.getEmail().equals(updateUser.getEmail())) {
         if (userRepository.findByEmail(updateUser.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exits");
         }
      }

      if (updateUser.getPhoneNumber() == null || updateUser.getPhoneNumber().isEmpty()) {
         throw new IllegalArgumentException("Phone number is required");
      }
      if (userRepository.findByPhoneNumber(user.getPhoneNumber()).isPresent()) {
         throw new IllegalArgumentException("Phonenumber already exists");
      }
      user.setUserName(updateUser.getUserName());
      user.setEmail(updateUser.getEmail());
      if (user.getEmail() == null || !user.getEmail().toLowerCase().endsWith("@gmail.com")) {
         throw new IllegalArgumentException("Email must be have format @gmail.com");
      }

      if (updateUser.getPassword() != null && !updateUser.getPassword().trim().isEmpty()) {
         user.setPassword(updateUser.getPassword());
      }
      user.setRole(updateUser.getRole());
      user.setStatus(updateUser.isStatus());
      user.setPhoneNumber(updateUser.getPhoneNumber());
      if (user.getPhoneNumber().length() != 10) {
         throw new IllegalArgumentException("Phone Number must be 10 numbers");
      }
      User saved = userRepository.save(user);
      return new UserManageDTO(saved);
   }

}
