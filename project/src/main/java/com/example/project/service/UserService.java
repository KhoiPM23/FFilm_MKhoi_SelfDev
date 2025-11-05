package com.example.project.service;

import com.example.project.dto.UserRegisterDto;  // THÊM DÒNG NÀY
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

   @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    private final UserRepository userRepository;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    public boolean isPasswordValid(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<User> getUserByRole(String role) {
        return userRepository.findByRole(role);
    }

    public Page<User> getUserByRole(String role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    public List<User> getUserByStatus(boolean status) {
        return userRepository.findByStatus(status);
    }

    public Page<User> getUserByStatus(boolean status, Pageable pageable) {
        return userRepository.findByStatus(status, pageable);
    }

    // SỬA: DÙNG DTO THAY VÌ ENTITY
    public User createUser(UserRegisterDto dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setUserName(dto.getUserName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword()); // @PrePersist sẽ hash
        user.setPhoneNumber(dto.getPhoneNumber());
        // role & status → @PrePersist tự set

        return userRepository.save(user);
    }

    public User updateUser(int id, User updateUser) {
        User existing = getUserById(id);
        existing.setUserName(updateUser.getUserName());
        existing.setEmail(updateUser.getEmail());
        if (updateUser.getPassword() != null && !updateUser.getPassword().isBlank()) {
            existing.setPassword(updateUser.getPassword());
        }
        existing.setRole(updateUser.getRole());
        existing.setStatus(updateUser.isStatus());
        existing.setPhoneNumber(updateUser.getPhoneNumber());
        return userRepository.save(existing);
    }

    public void deleteUser(int id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
        userRepository.deleteById(id);
    }
}