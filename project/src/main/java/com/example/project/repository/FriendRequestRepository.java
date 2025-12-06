package com.example.project.repository;

import com.example.project.model.FriendRequest;
import com.example.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    // Kiểm tra xem đã có lời mời chưa (để tránh spam)
    Optional<FriendRequest> findBySenderAndReceiver(User sender, User receiver);
    
    // Tìm lời mời đang chờ xử lý
    List<FriendRequest> findByReceiverAndStatus(User receiver, FriendRequest.Status status);
    
    // [FIX] Sửa 'f.sender.id' thành 'f.sender.userID'
    @Query("SELECT count(f) > 0 FROM FriendRequest f WHERE " +
           "(f.sender.userID = :u1 AND f.receiver.userID = :u2 AND f.status = 'ACCEPTED') OR " +
           "(f.sender.userID = :u2 AND f.receiver.userID = :u1 AND f.status = 'ACCEPTED')")
    boolean isFriend(@Param("u1") Integer u1, @Param("u2") Integer u2);

    // [THAY THẾ] Bỏ method findAcceptedFriends cũ gây lỗi ClassCastException
    // Thay bằng method này: Lấy toàn bộ lời mời đã chấp nhận liên quan đến user
    @Query("SELECT f FROM FriendRequest f " +
           "WHERE (f.sender.userID = :userId OR f.receiver.userID = :userId) " +
           "AND f.status = 'ACCEPTED'")
    List<FriendRequest> findAllAcceptedByUserId(@Param("userId") Integer userId);
}