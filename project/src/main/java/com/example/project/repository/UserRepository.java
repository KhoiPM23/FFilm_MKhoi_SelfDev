package com.example.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.project.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findById(int id);

    List<User> findByStatus(boolean status);

    List<User> findByRole(String role);

    List<User> findByRoleIn(List<String> roles);

    Page<User> findAll(Pageable pageable);

    Page<User> findByRole(String role, Pageable pageable);

    Page<User> findByEmail(String email, Pageable pageable);

    Page<User> findByStatus(boolean status, Pageable pageable);

    Page<User> findByRoleIn(List<String> roles, Pageable pageable);
}
