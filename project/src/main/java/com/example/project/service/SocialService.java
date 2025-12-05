package com.example.project.service;

import com.example.project.dto.PublicProfileDto;
import com.example.project.model.*;
import com.example.project.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SocialService {

    @Autowired private UserRepository userRepository;
    @Autowired private UserFollowRepository followRepository;
    @Autowired private WatchHistoryRepository historyRepository;
    @Autowired private FriendRequestRepository friendRequestRepository;
    @Autowired private NotificationService notificationService;

    @Autowired private FavoriteRepository favoriteRepository;

    @Transactional(readOnly = true)
    public PublicProfileDto getUserProfile(Integer viewerId, Integer targetUserId) {
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        PublicProfileDto profile = new PublicProfileDto();
        profile.setId(target.getUserID());
        profile.setName(target.getUserName());
        
        // Avatar Logic
        try {
            String safeName = URLEncoder.encode(target.getUserName(), StandardCharsets.UTF_8);
            profile.setAvatar("https://ui-avatars.com/api/?name=" + safeName + "&background=random&color=fff&size=200");
        } catch (Exception e) { profile.setAvatar("/images/placeholder-user.jpg"); }

        // 1. Xác định mối quan hệ & Following
        if (viewerId != null && viewerId.equals(targetUserId)) {
            profile.setRelationStatus("ME");
        } else if (viewerId != null) {
            User viewer = new User(); viewer.setUserID(viewerId);
            
            // Check Friend Status
            if (friendRequestRepository.isFriend(viewerId, targetUserId)) {
                profile.setRelationStatus("FRIEND");
            } else {
                var sent = friendRequestRepository.findBySenderAndReceiver(viewer, target);
                var received = friendRequestRepository.findBySenderAndReceiver(target, viewer);
                
                if (sent.isPresent() && sent.get().getStatus() == FriendRequest.Status.PENDING) profile.setRelationStatus("PENDING_SENT");
                else if (received.isPresent() && received.get().getStatus() == FriendRequest.Status.PENDING) profile.setRelationStatus("PENDING_RECEIVED");
                else profile.setRelationStatus("STRANGER");
            }
            
            // Check Following Status
            profile.setFollowing(followRepository.existsByFollowerAndFollowing(viewer, target));
        } else {
            profile.setRelationStatus("STRANGER");
            profile.setFollowing(false);
        }

        // 2. [FIX] ĐẾM SỐ LIỆU THẬT
        profile.setFollowerCount(followRepository.countByFollowing(target));
        profile.setFollowingCount(followRepository.countByFollower(target));
        
        // Lấy danh sách bạn bè (Status = ACCEPTED)
        // Lưu ý: Cần thêm method findFriendsByUser(userId) vào FriendRequestRepository hoặc dùng query custom
        // Ở đây tôi giả định dùng logic lọc từ FriendRequestRepository
        List<User> friendList = getFriendsListReal(target.getUserID());
        profile.setFriendCount(friendList.size());

        // 3. LOAD DATA (Check Privacy)
        
        // --- [FIX] LIST BẠN BÈ ---
        if (target.isPublicFriendList() || "ME".equals(profile.getRelationStatus()) || "FRIEND".equals(profile.getRelationStatus())) {
            List<PublicProfileDto.FriendDto> friendDtos = new ArrayList<>();
            for (User u : friendList) {
                PublicProfileDto.FriendDto dto = new PublicProfileDto.FriendDto();
                dto.setId(u.getUserID());
                dto.setName(u.getUserName());
                try {
                    String sName = URLEncoder.encode(u.getUserName(), StandardCharsets.UTF_8);
                    dto.setAvatar("https://ui-avatars.com/api/?name=" + sName + "&background=random&color=fff");
                } catch (Exception e) {}
                friendDtos.add(dto);
            }
            profile.setFriends(friendDtos);
        }

        // --- LIST YÊU THÍCH (Code cũ đã đúng, giữ nguyên) ---
        if (target.isPublicFavorites() || "ME".equals(profile.getRelationStatus())) {
            List<UserFavorite> favorites = favoriteRepository.findByUser(target);
            List<PublicProfileDto.MovieCardDto> favDtos = new ArrayList<>();
            for (UserFavorite fav : favorites) {
                if (fav.getMovie() != null) favDtos.add(mapToCardDto(fav.getMovie()));
            }
            profile.setFavoriteMovies(favDtos);
        }

        // --- LIST LỊCH SỬ (Code cũ đã đúng, giữ nguyên) ---
        if (target.isPublicWatchHistory() || "ME".equals(profile.getRelationStatus())) {
            var historyPage = historyRepository.findByUserOrderByLastWatchedAtDesc(target, PageRequest.of(0, 10));
            List<PublicProfileDto.MovieCardDto> historyList = new ArrayList<>();
            for (WatchHistory h : historyPage.getContent()) {
                if (h.getMovie() != null) historyList.add(mapToCardDto(h.getMovie()));
            }
            profile.setRecentWatchedMovies(historyList);
        }

        return profile;
    }

    // Helper lấy list friend 2 chiều
    private List<User> getFriendsListReal(Integer userId) {
        // Đây là logic phức tạp, tạm thời lấy tất cả request ACCEPTED liên quan đến user
        // Bạn nên viết Query trong Repo: SELECT * FROM FriendRequest WHERE (sender = id OR receiver = id) AND status = ACCEPTED
        // Dưới đây là giả lập logic đó bằng code Java (hơi chậm nếu data lớn, nhưng chạy đúng logic)
        List<com.example.project.model.FriendRequest> all = friendRequestRepository.findAll(); 
        List<User> friends = new ArrayList<>();
        for(com.example.project.model.FriendRequest fr : all) {
            if(fr.getStatus() == com.example.project.model.FriendRequest.Status.ACCEPTED) {
                if(fr.getSender().getUserID() == userId) friends.add(fr.getReceiver());
                else if(fr.getReceiver().getUserID() == userId) friends.add(fr.getSender());
            }
        }
        return friends;
    }

    // Helper map Card DTO (Giữ nguyên như cũ)
    private PublicProfileDto.MovieCardDto mapToCardDto(Movie m) {
        PublicProfileDto.MovieCardDto dto = new PublicProfileDto.MovieCardDto();
        dto.setId(m.getMovieID());
        dto.setTitle(m.getTitle());
        dto.setPoster(m.getPosterPath());
        dto.setBackdrop(m.getBackdropPath() != null ? m.getBackdropPath() : m.getPosterPath());
        dto.setRating(String.format("%.1f", m.getRating()));
        dto.setYear(m.getReleaseDate() != null ? m.getReleaseDate().toString().substring(0, 4) : "N/A");
        dto.setUrl("/movie/" + m.getMovieID());
        dto.setOverview(m.getDescription() != null ? m.getDescription() : "");
        return dto;
    }

    @Transactional
    public void followUser(Integer followerId, Integer followingId) {
        if (followerId.equals(followingId)) {
            throw new RuntimeException("Không thể tự follow chính mình");
        }
        
        User follower = userRepository.findById(followerId).orElseThrow(() -> new RuntimeException("Follower not found"));
        User following = userRepository.findById(followingId).orElseThrow(() -> new RuntimeException("Target user not found"));

        if (!followRepository.existsByFollowerAndFollowing(follower, following)) {
            followRepository.save(new UserFollow(follower, following));

            // Thông báo có avatar
            notificationService.createNotification(
                followingId, 
                follower.getUserName() + " đã bắt đầu theo dõi bạn.", 
                "FOLLOW", 
                null,
                follower
            );
        }
    }

    @Transactional
    public void unfollowUser(Integer followerId, Integer followingId) {
        User follower = new User(); follower.setUserID(followerId);
        User following = new User(); following.setUserID(followingId);
        followRepository.deleteByFollowerAndFollowing(follower, following);
    }

    // Lấy danh sách người dùng để chat (Giả lập danh sách bạn bè)
    // Trong thực tế, bạn có thể query từ bảng UserFollow để lấy danh sách "Friend" thật
    public List<User> getContactList(Integer currentUserId) {
        return userRepository.findAll().stream()
                .filter(u -> !Objects.equals(u.getUserID(), currentUserId))
                .toList();
    }

    // --- XỬ LÝ KẾT BẠN ---

    public void sendFriendRequest(Integer senderId, Integer receiverId) {
        if (senderId.equals(receiverId)) throw new RuntimeException("Không thể kết bạn với chính mình");

        User sender = userRepository.findById(senderId).orElseThrow();
        User receiver = userRepository.findById(receiverId).orElseThrow();

        Optional<FriendRequest> existing = friendRequestRepository.findBySenderAndReceiver(sender, receiver);
        if (existing.isPresent()) {
            throw new RuntimeException("Đã gửi lời mời trước đó");
        }

        FriendRequest req = new FriendRequest();
        req.setSender(sender);
        req.setReceiver(receiver);
        req.setStatus(FriendRequest.Status.PENDING);
        friendRequestRepository.save(req);
        
        // Gọi NotificationService Vipro (Truyền sender vào để lấy avatar)
        notificationService.createNotification(
            receiverId, 
            sender.getUserName() + " đã gửi lời mời kết bạn.", 
            "FRIEND_REQUEST", 
            null, // Link sẽ tự động tạo
            sender // Truyền sender để lấy avatar
        );
    }

    // [UPDATED] Chấp nhận kết bạn
    public void acceptFriendRequest(Integer receiverId, Integer senderId) {
        User sender = userRepository.findById(senderId).orElseThrow(); // Người gửi ban đầu
        User receiver = userRepository.findById(receiverId).orElseThrow(); // Mình (người nhận)

        FriendRequest req = friendRequestRepository.findBySenderAndReceiver(sender, receiver)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lời mời"));

        req.setStatus(FriendRequest.Status.ACCEPTED);
        friendRequestRepository.save(req);
        
        // Thông báo cho người gửi ban đầu là mình đã chấp nhận
        notificationService.createNotification(
            senderId, 
            receiver.getUserName() + " đã chấp nhận lời mời kết bạn.", 
            "FRIEND_ACCEPT",
            null,
            receiver // Truyền mình (receiver) làm sender của thông báo này
        );
    }
}