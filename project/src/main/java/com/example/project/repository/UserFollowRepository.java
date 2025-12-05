package com.example.project.repository;

import com.example.project.model.User;
import com.example.project.model.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    // Đếm người theo dõi mình
    long countByFollowing(User user);
    
    // Đếm người mình đang theo dõi
    long countByFollower(User user);
    
    // Check đã follow chưa
    boolean existsByFollowerAndFollowing(User follower, User following);
    
    // Hủy follow
    void deleteByFollowerAndFollowing(User follower, User following);
}