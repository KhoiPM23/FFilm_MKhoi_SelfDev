package com.example.project.service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
@Service
public class UserService {
 @Autowired
 private UserRepository userRepository;
 public List<User> getAllUsers(){
   return userRepository.findAll();
 }

 public Page<User> getAllUsers(Pageable pageable){
    return userRepository.findAll(pageable);
 }

 public List<User> getUserByRole(String role){
   return userRepository.findByRole(role);
 }
 public Page<User> getUserByRole(String role, Pageable pageable){
    return userRepository.findByRole(role, pageable);
 }

 public Page<User> getUserByEmail(String email, Pageable pageable){
    return userRepository.findByEmail(email, pageable);
 }

 public List<User> getUserByStatus(boolean status)
{
   return userRepository.findByStatus(status);
}
 public Page<User> getUserByStatus(boolean status, Pageable pageable){
    return userRepository.findByStatus(status, pageable);
 }

 public User createUser(User user){
   if (userRepository.findByEmail(user.getEmail()) .isPresent()){
      throw new IllegalArgumentException("Email already exists");
   }
   return userRepository.save(user);
 }

 public void deleteUser(int id){
   userRepository.deleteById(id);
 }

public User updateUser(int id, User updateUser){
Optional<User> existing = userRepository.findById(id);
if (existing.isEmpty()){
   throw new IllegalArgumentException("User not found");
}
User user = existing.get();
user.setUserName(updateUser.getUserName());
user.setEmail(updateUser.getEmail());
if (updateUser.getPassword() != null && !updateUser.getPassword().isEmpty()){
   user.setPassword(updateUser.getPassword());
}
user.setRole(updateUser.getRole());
user.setStatus(updateUser.isStatus());
user.setPhoneNumber(updateUser.getPhoneNumber());
return userRepository.save(user);
}

 }
