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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;

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
        try {
            profile.setFollowerCount(followRepository.countByFollowing(target));
            profile.setFollowingCount(followRepository.countByFollower(target));
        } catch (Exception e) {
            profile.setFollowerCount(0);
            profile.setFollowingCount(0);
        }
        
        List<User> friendList = getFriendsListReal(target.getUserID());
        profile.setFriendCount(friendList.size());

        // 3. LOAD DATA (Check Privacy)
        profile.setFriends(new ArrayList<>());
        profile.setFavoriteMovies(new ArrayList<>());
        profile.setRecentWatchedMovies(new ArrayList<>());
        
        // --- [FIX] LIST BẠN BÈ ---
        try {
            if (target.isPublicFriendList() || "ME".equals(profile.getRelationStatus()) || "FRIEND".equals(profile.getRelationStatus())) {
                List<PublicProfileDto.FriendDto> friendDtos = new ArrayList<>();
                for (User u : friendList) {
                    if (u == null) continue;
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
        } catch (Exception ignored) {}

        // List Yêu thích
        try {
            if (target.isPublicFavorites() || "ME".equals(profile.getRelationStatus())) {
                List<UserFavorite> favorites = favoriteRepository.findByUser(target);
                if (favorites != null) {
                    List<Map<String, Object>> favList = new ArrayList<>();
                    for (UserFavorite fav : favorites) {
                        if (fav != null && fav.getMovie() != null) favList.add(mapToCardDto(fav.getMovie()));
                    }
                    profile.setFavoriteMovies(favList);
                }
            }
        } catch (Exception ignored) {}

        // List Lịch sử
        try {
            if (target.isPublicWatchHistory() || "ME".equals(profile.getRelationStatus())) {
                var historyPage = historyRepository.findByUserOrderByLastWatchedAtDesc(target, PageRequest.of(0, 10));
                if (historyPage != null && historyPage.getContent() != null) {
                    List<Map<String, Object>> historyList = new ArrayList<>();
                    for (WatchHistory h : historyPage.getContent()) {
                        if (h != null && h.getMovie() != null) historyList.add(mapToCardDto(h.getMovie()));
                    }
                    profile.setRecentWatchedMovies(historyList);
                }
            }
        } catch (Exception ignored) {}

        return profile;
    }

    // Helper lấy list friend 2 chiều
    private List<User> getFriendsListReal(Integer userId) {
        if (userId == null) return new ArrayList<>();
        try {
            List<com.example.project.model.FriendRequest> acceptedRequests = friendRequestRepository.findAllAcceptedByUserId(userId); 
            List<User> friends = new ArrayList<>();
            if (acceptedRequests != null) {
                for (com.example.project.model.FriendRequest fr : acceptedRequests) {
                    if (fr == null) continue;
                    if (fr.getSender() != null && fr.getSender().getUserID() == userId.intValue() && fr.getReceiver() != null) {
                        friends.add(fr.getReceiver());
                    } else if (fr.getReceiver() != null && fr.getReceiver().getUserID() == userId.intValue() && fr.getSender() != null) {
                        friends.add(fr.getSender());
                    }
                }
            }
            return friends;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // Hàm chuyển đổi Movie Entity sang Map (Dữ liệu chuẩn cho Hover Card)
    private Map<String, Object> mapToCardDto(Movie m) {
        Map<String, Object> map = new HashMap<>();
        
        // 1. ID & Cơ bản
        map.put("id", m.getMovieID());
        map.put("tmdbId", m.getTmdbId()); 
        map.put("title", m.getTitle());
        
        // --- [FIX] XỬ LÝ ẢNH (POSTER & BACKDROP) ---
        // Logic: Nếu path bắt đầu bằng "/", nối thêm domain TMDB. Nếu không, dùng nguyên gốc.
        String tmdbBaseUrl = "https://image.tmdb.org/t/p/w500"; 
        String tmdbBackdropUrl = "https://image.tmdb.org/t/p/original"; // Backdrop cần nét hơn

        // Xử lý Poster
        String rawPoster = m.getPosterPath();
        String finalPoster = "/images/placeholder.jpg";
        if (rawPoster != null && !rawPoster.isEmpty()) {
            if (rawPoster.startsWith("/")) {
                finalPoster = tmdbBaseUrl + rawPoster;
            } else {
                finalPoster = rawPoster; // Trường hợp là link http ảnh ngoài
            }
        }
        map.put("poster", finalPoster);

        // Xử lý Backdrop (Quan trọng cho Hover Card)
        String rawBackdrop = m.getBackdropPath();
        String finalBackdrop = finalPoster; // Fallback lấy poster nếu không có backdrop
        if (rawBackdrop != null && !rawBackdrop.isEmpty()) {
            if (rawBackdrop.startsWith("/")) {
                finalBackdrop = tmdbBackdropUrl + rawBackdrop;
            } else {
                finalBackdrop = rawBackdrop;
            }
        }
        map.put("backdrop", finalBackdrop);

        // --- [FIX] TRAILER ---
        // Đảm bảo key này có dữ liệu. Nếu null, hover card sẽ tự ẩn video.
        map.put("trailerKey", m.getTrailerKey()); 

        // 3. Meta Data & Date (Quan trọng cho substring trong HTML)
        map.put("rating", m.getRating() > 0 ? String.format("%.1f", m.getRating()) : "N/A");
        
        // Chuyển Date sang String để HTML substring(0,4) không bị lỗi
        if (m.getReleaseDate() != null) {
            map.put("releaseDate", m.getReleaseDate().toString()); 
            map.put("year", m.getReleaseDate().toString().substring(0, 4));
        } else {
            map.put("releaseDate", "");
            map.put("year", "—");
        }

        // 4. Các thông tin phụ
        map.put("url", "/movie/" + m.getMovieID());
        map.put("overview", m.getDescription() != null ? m.getDescription() : "Chưa có mô tả.");
        map.put("runtime", m.getDuration() > 0 ? m.getDuration() + " phút" : "N/A");
        map.put("contentRating", m.getContentRating() != null ? m.getContentRating() : "G");
        map.put("country", m.getCountry() != null ? m.getCountry() : "Quốc tế");

        // 5. Thể loại (Genres) - Mapping sang List Map đơn giản để HTML dễ đọc
        List<Map<String, Object>> genreList = new ArrayList<>();
        if (m.getGenres() != null) {
            for (Genre g : m.getGenres()) {
                Map<String, Object> gMap = new HashMap<>();
                gMap.put("id", g.getGenreID());
                gMap.put("name", g.getName());
                genreList.add(gMap);
            }
        }
        map.put("genres", genreList);

        return map;
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

    // [NEW] Hàm Hủy Kết Bạn
    @Transactional
    public void unfriendUser(Integer userId1, Integer userId2) {
        User u1 = userRepository.findById(userId1).orElseThrow();
        User u2 = userRepository.findById(userId2).orElseThrow();

        // Xóa request 2 chiều (bất kể ai gửi trước)
        var req1 = friendRequestRepository.findBySenderAndReceiver(u1, u2);
        req1.ifPresent(friendRequestRepository::delete);

        var req2 = friendRequestRepository.findBySenderAndReceiver(u2, u1);
        req2.ifPresent(friendRequestRepository::delete);
    }
}