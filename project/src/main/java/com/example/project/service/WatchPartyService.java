package com.example.project.service;

import com.example.project.dto.RoomMember;
import com.example.project.dto.SocketMessage;
import com.example.project.model.User;
import com.example.project.model.WatchRoom;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.UserRepository;
import com.example.project.repository.WatchRoomRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class WatchPartyService {

    @Autowired private WatchRoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private FriendRequestRepository friendRequestRepository;

    // --- PHẦN 1: TƯƠNG TÁC DATABASE & LOBBY ---

    public List<WatchRoom> getMyRooms(Integer userId) {
        User user = new User(); user.setUserID(userId);
        return roomRepository.findByOwner(user);
    }

    /**
     * Lấy danh sách phòng cho Lobby:
     * 1. Lấy tất cả phòng PUBLIC từ Database (Persistence)
     * 2. Merge với dữ liệu Realtime từ RAM (Live Status)
     */
    public List<Map<String, Object>> getLobbyRooms() {
        // 1. Lấy phòng từ DB
        List<WatchRoom> dbRooms = roomRepository.findAll(); 
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (WatchRoom dbRoom : dbRooms) {
            Map<String, Object> roomData = new HashMap<>();
            
            // [FIX] Đổi key 'id' thành 'roomId' để khớp với HTML
            roomData.put("roomId", dbRoom.getId()); 
            roomData.put("roomName", dbRoom.getName());
            roomData.put("accessType", dbRoom.getAccessType());
            roomData.put("maxUsers", dbRoom.getMaxUsers());
            
            // Xử lý Owner Name & Avatar
            String ownerName = "Unknown";
            if (dbRoom.getOwner() != null) {
                ownerName = dbRoom.getOwner().getUserName();
            }
            roomData.put("ownerName", ownerName);
            
            // Tạo avatar tự động
            try {
                String safeName = URLEncoder.encode(ownerName, StandardCharsets.UTF_8);
                String avatarUrl = "https://ui-avatars.com/api/?name=" + safeName + "&background=random&color=fff&size=64";
                roomData.put("ownerAvatar", avatarUrl);
            } catch (Exception e) {
                roomData.put("ownerAvatar", "/images/placeholder-user.jpg");
            }
            
            // 2. Check trạng thái Realtime (RAM)
            String runtimeId = String.valueOf(dbRoom.getId());
            WatchRoomRuntime runtime = activeRooms.get(runtimeId);
            
            if (runtime != null) {
                roomData.put("isLive", true);
                roomData.put("memberCount", runtime.getMembers().size());
                roomData.put("currentMovie", runtime.getCurrentMovieTitle());
                roomData.put("poster", runtime.getCurrentMoviePoster());
            } else {
                roomData.put("isLive", false);
                roomData.put("memberCount", 0);
                roomData.put("currentMovie", "Chưa chiếu");
                roomData.put("poster", "/images/placeholder.jpg"); // Ảnh mặc định khi không chiếu
            }
            
            result.add(roomData);
        }
        
        // Sắp xếp: Phòng đang Live lên đầu
        result.sort((r1, r2) -> {
            boolean live1 = (boolean) r1.get("isLive");
            boolean live2 = (boolean) r2.get("isLive");
            return Boolean.compare(live2, live1);
        });
        
        return result;
    }

    @Transactional
    public WatchRoom createPersistentRoom(Integer userId, String name, int maxUsers, String password) {
        User owner = userRepository.findById(userId).orElseThrow();
        long currentCount = roomRepository.countByOwner(owner);
        if (currentCount >= 5) throw new RuntimeException("Đạt giới hạn 5 phòng.");

        WatchRoom room = new WatchRoom();
        room.setName(name);
        room.setMaxUsers(maxUsers);
        room.setOwner(owner);
        room.setActive(false);
        if (password != null && !password.trim().isEmpty()) {
            room.setAccessType("PRIVATE");
            room.setPassword(password);
        } else {
            room.setAccessType("PUBLIC");
        }
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Integer userId, Long roomId) {
        WatchRoom room = roomRepository.findById(roomId).orElseThrow();
        if (room.getOwner().getUserID() != userId) throw new RuntimeException("Không có quyền.");
        
        String runtimeId = String.valueOf(roomId);
        activeRooms.remove(runtimeId);
        roomRepository.delete(room);
    }
    
    public WatchRoom getRoomInfo(Long roomId) {
        return roomRepository.findById(roomId).orElse(null);
    }

    // --- PHẦN 2: LOGIC RUNTIME (RAM) ---
    private final Map<String, WatchRoomRuntime> activeRooms = new ConcurrentHashMap<>();

    @Data
    public static class WatchRoomRuntime {
        private String roomId;
        private String hostSessionId;
        private String hostName;
        private String hostAvatar;
        
        private Integer currentMovieId;
        private String currentMovieUrl;
        private String currentMovieTitle;
        private String currentMoviePoster;
        
        private Map<String, RoomMember> members = new ConcurrentHashMap<>();
        private Map<String, RoomMember> waitingList = new ConcurrentHashMap<>();
        private List<SocketMessage> chatHistory = Collections.synchronizedList(new ArrayList<>());

        public WatchRoomRuntime(String roomId, String hostSessionId) {
            this.roomId = roomId;
            this.hostSessionId = hostSessionId;
        }
        
        public void addChat(SocketMessage msg) {
            if (chatHistory.size() >= 100) chatHistory.remove(0);
            if (msg.getId() == null) msg.setId(UUID.randomUUID().toString());
            chatHistory.add(msg);
        }
        
        public int getMemberCount() {
            return members.size();
        }
    }

    public void startRoom(String roomId, RoomMember host) {
        WatchRoomRuntime runtime = new WatchRoomRuntime(roomId, host.getSessionId());
        runtime.setHostName(host.getUserName());
        runtime.setHostAvatar(host.getAvatar());
        runtime.getMembers().put(host.getSessionId(), host);
        activeRooms.put(roomId, runtime);
        updateRoomActiveStatus(Long.valueOf(roomId), true);
    }

    // Logic xin vào phòng
    public String requestJoin(String roomId, RoomMember member) {
        WatchRoomRuntime runtime = activeRooms.get(roomId);
        if (runtime == null) return "NOT_FOUND";
        
        WatchRoom dbRoom = getRoomInfo(Long.valueOf(roomId));
        boolean needApproval = "PRIVATE".equals(dbRoom.getAccessType());

        if (needApproval) {
            runtime.getWaitingList().put(member.getSessionId(), member);
            return "WAITING";
        } else {
            runtime.getMembers().put(member.getSessionId(), member);
            return "JOINED";
        }
    }

    public boolean approveMember(String roomId, String sessionId) {
        WatchRoomRuntime runtime = activeRooms.get(roomId);
        if (runtime != null && runtime.getWaitingList().containsKey(sessionId)) {
            RoomMember member = runtime.getWaitingList().remove(sessionId);
            runtime.getMembers().put(sessionId, member);
            return true;
        }
        return false;
    }

    public void kickMember(String roomId, String sessionId) {
        WatchRoomRuntime runtime = activeRooms.get(roomId);
        if (runtime != null) {
            runtime.getMembers().remove(sessionId);
            runtime.getWaitingList().remove(sessionId);
        }
    }

    public WatchRoomRuntime getRuntimeRoom(String roomId) {
        return activeRooms.get(roomId);
    }
    
    public Collection<WatchRoomRuntime> getAllActiveRuntimeRooms() {
        return activeRooms.values();
    }

    // Logic thoát phòng & chuyển Host (như cũ)
    public String handleDisconnect(String sessionId) {
        for (WatchRoomRuntime room : activeRooms.values()) {
            if (room.getMembers().containsKey(sessionId)) {
                room.getMembers().remove(sessionId);
                if (sessionId.equals(room.getHostSessionId())) {
                    if (room.getMembers().isEmpty()) {
                        activeRooms.remove(room.getRoomId());
                        updateRoomActiveStatus(Long.valueOf(room.getRoomId()), false);
                        return null; 
                    } else {
                        String newHostId = room.getMembers().keySet().iterator().next();
                        room.setHostSessionId(newHostId);
                        return newHostId; 
                    }
                }
                return "MEMBER_LEFT";
            }
            if (room.getWaitingList().containsKey(sessionId)) {
                room.getWaitingList().remove(sessionId);
            }
        }
        return null;
    }

    private void updateRoomActiveStatus(Long roomId, boolean status) {
        WatchRoom dbRoom = roomRepository.findById(roomId).orElse(null);
        if (dbRoom != null) {
            dbRoom.setActive(status);
            roomRepository.save(dbRoom);
        }
    }

    /**
     * 1. Hàm tính thời gian Last Active (Vipro Format: 1m, 1h, 1d...)
     */
    private String calculateLastActive(Date lastLogin) {
        if (lastLogin == null) return "Long time ago";
        long diff = new Date().getTime() - lastLogin.getTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m";
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        if (hours < 24) return hours + "h";
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        if (days < 7) return days + "d";
        if (days < 30) return (days / 7) + "w";
        if (days < 365) return (days / 30) + "mo";
        return (days / 365) + "y";
    }

    /**
     * Lấy danh sách Social Users:
     * 1. CHỈ lấy Role USER (Bỏ Admin/Staff).
     * 2. Check Online dựa trên kết nối Socket thực tế (Real-time).
     */
    public List<Map<String, Object>> getSocialUsers(Integer currentUserId) {
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> socialList = new ArrayList<>();

        // Lấy danh sách user đang online thật sự từ WebSocket Registry
        Set<String> onlineUsernames = activeRooms.values().stream() // Hoặc logic lấy từ userRegistry
                .flatMap(r -> r.getMembers().values().stream())
                .map(m -> m.getUserName())
                .collect(Collectors.toSet());
        // Cách chuẩn hơn dùng userRegistry:
        Set<String> realOnlineUsers = userRegistry.getUsers().stream()
                .map(u -> u.getName()) // Lưu ý: Principal name phải match với username
                .collect(Collectors.toSet());

        for (User u : allUsers) {
            // [FIX 1] Chỉ hiển thị Role USER
            if (u.getUserID() == currentUserId) continue;
            if (!"USER".equalsIgnoreCase(u.getRole())) continue; 

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", u.getUserID());
            userMap.put("name", u.getUserName());
            
            // Avatar logic
            try {
                String safeName = URLEncoder.encode(u.getUserName(), StandardCharsets.UTF_8);
                userMap.put("avatar", "https://ui-avatars.com/api/?name=" + safeName + "&background=random&color=fff");
            } catch (Exception e) { userMap.put("avatar", "/images/placeholder-user.jpg"); }

            // [FIX 2] Check Online Real-time
            // So sánh username của user này có nằm trong danh sách đang kết nối Socket không
            boolean isOnline = realOnlineUsers.contains(u.getUserName());
            userMap.put("isOnline", isOnline);
            
            // Logic quan hệ (Mock tạm để UI hoạt động, cần bảng FriendRequest thật)
            // Bạn cần query bảng FriendRequest/UserFollow ở đây
            userMap.put("relation", "Stranger"); // Stranger, Pending, Friend
            
            // Fake last active nếu offline
            if (!isOnline) userMap.put("lastActive", "1h"); 

            // [FIX LOGIC QUAN HỆ CHÍNH XÁC]
            String relation = "STRANGER";
            
            // 1. Kiểm tra đã là bạn bè chưa
            if (friendRequestRepository.isFriend(currentUserId, u.getUserID())) {
                relation = "FRIEND";
            } else {
                User me = new User(); me.setUserID(currentUserId);
                
                // 2. Kiểm tra mình đã gửi chưa (PENDING_SENT)
                if (friendRequestRepository.findBySenderAndReceiver(me, u).isPresent()) {
                    relation = "PENDING_SENT"; 
                } 
                // 3. Kiểm tra họ đã gửi cho mình chưa (PENDING_RECEIVED) -> Để hiện nút Chấp nhận
                else if (friendRequestRepository.findBySenderAndReceiver(u, me).isPresent()) {
                    relation = "PENDING_RECEIVED";
                }
            }
            userMap.put("relation", relation); // Frontend sẽ dùng cái này để if/else nút

            socialList.add(userMap);
        }
        return socialList;
    }

    /**
     * 3. Lấy Lobby Rooms (Nâng cấp đầy đủ badge, max user, recommend)
     */
    public Map<String, List<Map<String, Object>>> getLobbyDataVipro(Integer userId) {
        List<WatchRoom> allDbRooms = roomRepository.findAll();
        
        List<Map<String, Object>> allRooms = new ArrayList<>();
        List<Map<String, Object>> recommendedRooms = new ArrayList<>();
        List<Map<String, Object>> hotRooms = new ArrayList<>();

        // Lấy sở thích user (Ví dụ mock list ID thể loại, thực tế query từ UserFavorite)
        List<String> userFavoriteGenres = List.of("Action", "Anime"); 

        for (WatchRoom dbRoom : allDbRooms) {
            Map<String, Object> roomData = new HashMap<>();
            roomData.put("roomId", dbRoom.getId());
            roomData.put("roomName", dbRoom.getName());
            roomData.put("accessType", dbRoom.getAccessType()); // PUBLIC/PRIVATE
            roomData.put("maxUsers", dbRoom.getMaxUsers());
            
            // Owner Info
            String ownerName = dbRoom.getOwner() != null ? dbRoom.getOwner().getUserName() : "Unknown";
            roomData.put("ownerName", ownerName);
            try {
                String safeName = URLEncoder.encode(ownerName, StandardCharsets.UTF_8);
                roomData.put("ownerAvatar", "https://ui-avatars.com/api/?name=" + safeName + "&background=random&color=fff");
            } catch (Exception e) { roomData.put("ownerAvatar", "/images/placeholder-user.jpg"); }

            // Realtime Data
            String runtimeId = String.valueOf(dbRoom.getId());
            WatchRoomRuntime runtime = activeRooms.get(runtimeId);
            
            int currentMembers = 0;
            boolean isLive = false;
            String currentGenre = "";

            if (runtime != null) {
                isLive = true;
                currentMembers = runtime.getMembers().size();
                roomData.put("currentMovie", runtime.getCurrentMovieTitle());
                roomData.put("poster", runtime.getCurrentMoviePoster());
                // roomData.put("genre", runtime.getCurrentMovieGenre()); // Cần lưu genre vào runtime
            } else {
                roomData.put("currentMovie", "Đang chờ...");
                roomData.put("poster", "/images/placeholder.jpg");
            }
            
            roomData.put("isLive", isLive);
            roomData.put("memberCount", currentMembers);
            
            // Logic phân loại danh sách
            allRooms.add(roomData);
            
            // Logic Gợi ý (Chỉ gợi ý phòng đang Live)
            if (isLive) {
                // Nếu genre trùng sở thích -> Add vào recommended
                // if (userFavoriteGenres.contains(currentGenre)) recommendedRooms.add(roomData);
                
                // Nếu đông người xem -> Add vào Hot
                if (currentMembers > 5) hotRooms.add(roomData);
            }
        }
        
        // Sắp xếp Live lên đầu cho list All
        allRooms.sort((r1, r2) -> Boolean.compare((boolean)r2.get("isLive"), (boolean)r1.get("isLive")));

        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("all", allRooms);
        result.put("recommended", recommendedRooms.isEmpty() ? allRooms.subList(0, Math.min(allRooms.size(), 3)) : recommendedRooms); // Fallback lấy 3 phòng đầu nếu ko có recommend
        result.put("hot", hotRooms);
        
        return result;
    }

    public WatchRoom createRoom(String name, String accessType, String password, int maxUsers, int ownerId) {
        WatchRoom room = new WatchRoom();
        room.setName(name);
        room.setAccessType(accessType);
        room.setPassword(password);
        room.setMaxUsers(maxUsers);
        room.setActive(true); // Mặc định phòng mới tạo là Active
        
        // [FIX 1] Bỏ dòng setCreatedAt vì Entity đã có @PrePersist lo tự động rồi
        // room.setCreatedAt(LocalDateTime.now()); <--- Không cần dòng này nữa

        // [FIX 2] Set Owner chuẩn theo Entity User
        User owner = new User();
        owner.setUserID(ownerId); // User dùng int userID
        room.setOwner(owner);

        // Lưu vào Database
        WatchRoom savedRoom = roomRepository.save(room);

        // [LOGIC VIPRO] Khởi tạo Runtime RAM để hiện ngay lên Lobby
        // Use existing constructor; hostSessionId unknown at creation time -> pass null
        WatchRoomRuntime runtime = new WatchRoomRuntime(String.valueOf(savedRoom.getId()), null);
        
        // Khởi tạo các list rỗng để tránh lỗi Null (class already initializes them, but ensure safe defaults)
        runtime.setMembers(new java.util.concurrent.ConcurrentHashMap<>());
        runtime.setWaitingList(new java.util.concurrent.ConcurrentHashMap<>());
        runtime.setChatHistory(java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        
        // Set info mặc định
        runtime.setCurrentMovieTitle("Đang chọn phim...");
        runtime.setCurrentMoviePoster("/images/placeholder.jpg");

        // Đưa vào Map quản lý Active
        activeRooms.put(String.valueOf(savedRoom.getId()), runtime);

        return savedRoom;
    }
}