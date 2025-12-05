package com.example.project.repository;

import com.example.project.model.FriendRequest;
import com.example.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    // Kiểm tra xem đã có lời mời chưa (để tránh spam)
    Optional<FriendRequest> findBySenderAndReceiver(User sender, User receiver);
    
    // Tìm lời mời đang chờ xử lý
    List<FriendRequest> findByReceiverAndStatus(User receiver, FriendRequest.Status status);
    
    // Kiểm tra quan hệ bạn bè (2 chiều)
    @Query("SELECT count(f) > 0 FROM FriendRequest f WHERE " +
           "(f.sender.id = :u1 AND f.receiver.id = :u2 AND f.status = 'ACCEPTED') OR " +
           "(f.sender.id = :u2 AND f.receiver.id = :u1 AND f.status = 'ACCEPTED')")
    boolean isFriend(Integer u1, Integer u2);
}