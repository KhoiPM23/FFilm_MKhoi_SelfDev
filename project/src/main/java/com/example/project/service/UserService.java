package com.example.project.service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
@Service
public class UserService {
 @Autowired
 private UserRepository userRepository;
 
 public Page<User> getAllUsers(Pageable pageable){
    return userRepository.findAll(pageable);
 }
 
 public Page<User> getUserByRole(String role, Pageable pageable)){
    return userRepository.findByRole(role, pageable);
 }

 public Page<User> getUserByEmail(String email, Pageable pageable){
    return userRepository.findByEmail(email, pageable);
 }

 public Page<User> getUserByStatus(boolean status, Pageable pageable){
    return userRepository.findByStatus(status, pageable);
 }

}