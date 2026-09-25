package com.example.project.controller;

import com.example.project.dto.RoomMember;
import com.example.project.dto.SocketMessage;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.WatchRoom;
import com.example.project.service.WatchPartyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
public class WatchPartyController {

    @Autowired private WatchPartyService partyService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // --- HELPER: Lấy User từ Session ---
    private UserSessionDto getUserFromSession(HttpSession session) {
        return (UserSessionDto) session.getAttribute("user");
    }

    // --- VIEW HANDLERS ---

    @GetMapping("/my-rooms")
    public String myRooms(Model model, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return "redirect:/login";

        List<WatchRoom> rooms = partyService.getMyRooms(user.getId());
        model.addAttribute("rooms", rooms);
        return "watch-party/my-rooms";
    }

    @GetMapping("/watch-party")
    public String lobby(Model model, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return "redirect:/login";

        // Lấy dữ liệu Vipro từ Service
        Map<String, List<Map<String, Object>>> lobbyData = partyService.getLobbyDataVipro(user.getId());
        
        model.addAttribute("recommendedRooms", lobbyData.get("recommended"));
        model.addAttribute("hotRooms", lobbyData.get("hot"));
        model.addAttribute("activeRooms", lobbyData.get("all"));
        
        // Lấy Social Sidebar Data
        model.addAttribute("socialUsers", partyService.getSocialUsers(user.getId()));
        
        return "watch-party/lobby";
    }

    @PostMapping("/watch-party/create")
    public String createRoom(@RequestParam("name") String name,
                             @RequestParam("accessType") String accessType,
                             @RequestParam(value = "password", required = false) String password,
                             @RequestParam(value = "maxUsers", defaultValue = "10") int maxUsers,
                             HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return "redirect:/login";

        // Gọi hàm service tạo phòng & khởi tạo RAM
        WatchRoom newRoom = partyService.createRoom(name, accessType, password, maxUsers, user.getId());
        
        return "redirect:/watch-party/room/" + newRoom.getId();
    }

    @GetMapping("/watch-party/room/{roomId}")
    public String joinRoom(@PathVariable Long roomId, Model model, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return "redirect:/login";

        WatchRoom dbRoom = partyService.getRoomInfo(roomId);
        if (dbRoom == null) return "redirect:/watch-party?error=notfound";

        // Logic check Runtime (RAM) để đảm bảo đồng bộ
        String runtimeId = String.valueOf(roomId);
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(runtimeId);
        
        // Nếu phòng chưa có trong RAM (do mới khởi động lại server), start lại nó
        if (runtime == null) {
             // Tự động start nếu là chủ phòng, hoặc báo lỗi nếu là khách
             if (dbRoom.getOwner().getUserID() == user.getId()) {
                 // Mock object member để start
                 com.example.project.dto.RoomMember hostMember = new com.example.project.dto.RoomMember(
                     session.getId(), user.getId(), user.getUserName(), null, null, false, false
                 );
                 partyService.startRoom(runtimeId, hostMember);
                 runtime = partyService.getRuntimeRoom(runtimeId);
             } else {
                 return "redirect:/watch-party?error=room_not_active";
             }
        } else if (runtime.getHostUserId() == null && dbRoom.getOwner().getUserID() == user.getId()) {
             runtime.setHostUserId(user.getId());
             runtime.setHostName(user.getUserName());
             runtime.getApprovedUserIds().add(user.getId());
        }

        model.addAttribute("room", dbRoom);
        model.addAttribute("user", user);
        
        // Check quyền Host: Check runtime host first, fallback to DB owner
        boolean isHost = (runtime != null && runtime.getHostUserId() != null)
                ? runtime.getHostUserId().equals(user.getId())
                : dbRoom.getOwner().getUserID() == user.getId();
        model.addAttribute("isHost", isHost);
        
        // Check trạng thái Join (Waiting/Joined)
        String joinStatus = "JOINED";
        if (!isHost && "PRIVATE".equals(dbRoom.getAccessType())) {
             if (runtime == null || !runtime.getApprovedUserIds().contains(user.getId())) {
                 joinStatus = "WAITING"; 
             }
        }
        model.addAttribute("joinStatus", joinStatus); 
        
        return "watch-party/room";
    }

    @DeleteMapping("/api/party/delete/{roomId}")
    @ResponseBody
    public ResponseEntity<?> deleteRoomApi(@PathVariable Long roomId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        try {
            partyService.deleteRoom(user.getId(), roomId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/party/close/{roomId}")
    @ResponseBody
    public ResponseEntity<?> closeRoomApi(@PathVariable Long roomId, HttpSession session) {
        UserSessionDto user = getUserFromSession(session);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        
        WatchRoom dbRoom = partyService.getRoomInfo(roomId);
        if (dbRoom == null) return ResponseEntity.notFound().build();
        
        String runtimeId = String.valueOf(roomId);
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(runtimeId);
        boolean isHost = (runtime != null && runtime.getHostUserId() != null)
                ? runtime.getHostUserId().equals(user.getId())
                : dbRoom.getOwner().getUserID() == user.getId();
                
        if (!isHost) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        
        Map<String, Object> closeMsg = Map.of("type", "ROOM_CLOSED", "message", "Chủ phòng đã đóng phòng chiếu.");
        messagingTemplate.convertAndSend("/topic/party/" + roomId + "/system", closeMsg);
        
        partyService.closeRoom(runtimeId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- WEBSOCKET HANDLERS ---

    @MessageMapping("/party/{roomId}/chat")
    public void chat(@DestinationVariable String roomId, @Payload SocketMessage msg, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (runtime != null) {
            // Override sender with authenticated username from WebSocket session attributes
            if (headerAccessor.getSessionAttributes() != null && headerAccessor.getSessionAttributes().containsKey("userName")) {
                msg.setSender((String) headerAccessor.getSessionAttributes().get("userName"));
            }
            // Service tự động set ID + timestamp
            msg.setTimestamp(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            runtime.addChat(msg);
        }
        messagingTemplate.convertAndSend("/topic/party/" + roomId + "/chat", msg);
    }

    @MessageMapping("/party/{roomId}/join")
    public void joinRoomStomp(@DestinationVariable String roomId, @Payload Map<String, String> payload, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        String httpSessionId = (String) headerAccessor.getSessionAttributes().get("httpSessionId");
        UserSessionDto user = (UserSessionDto) headerAccessor.getSessionAttributes().get("userDto");
        
        if (httpSessionId == null || user == null) return;

        RoomMember member = new RoomMember(httpSessionId, user.getId(), user.getUserName(), null, null, false, false);
        String status = partyService.requestJoin(roomId, member);

        if ("WAITING".equals(status)) {
            // Notify Host
            WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
            if (runtime != null) {
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/waitingUpdate", runtime.getWaitingList().values());
            }
        } else if ("JOINED".equals(status)) {
            // Broadcast new member joined
            Map<String, Object> joinMsg = new HashMap<>();
            joinMsg.put("type", "MEMBER_JOINED");
            joinMsg.put("sessionId", httpSessionId);
            joinMsg.put("userName", user.getUserName());
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/system", joinMsg);
        }
    }

    @MessageMapping("/party/{roomId}/getHistory")
    public void getChatHistory(@DestinationVariable String roomId, @Payload Map<String, String> payload) {
        String userSessionId = payload.get("sessionId");
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        
        if (runtime != null) {
            // 1. Trả về lịch sử chat
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/history/" + userSessionId, runtime.getChatHistory());
            
            // 2. [QUAN TRỌNG] Trả về phim đang chiếu để đồng bộ người mới vào
            if (runtime.getCurrentMovieUrl() != null) {
                // Calculate estimated current time based on last sync if it was playing
                double currentEstimatedTime = runtime.getCurrentPlaybackTime();
                if ("PLAY".equals(runtime.getPlaybackStatus())) {
                    currentEstimatedTime += (System.currentTimeMillis() - runtime.getLastSyncTimestamp()) / 1000.0;
                }
                
                Map<String, Object> movieData = Map.of(
                    "id", runtime.getCurrentMovieId() != null ? runtime.getCurrentMovieId() : 0,
                    "title", runtime.getCurrentMovieTitle() != null ? runtime.getCurrentMovieTitle() : "",
                    "url", runtime.getCurrentMovieUrl()
                );
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/loadMovie", movieData);
                
                Map<String, Object> syncData = new HashMap<>();
                syncData.put("type", runtime.getPlaybackStatus());
                syncData.put("currentTime", currentEstimatedTime);
                syncData.put("sender", "System");
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/sync", syncData);
            }
            
            // 3. Trả về danh sách thành viên hiện tại (để WebRTC kết nối)
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/members/" + userSessionId, runtime.getMembers().values());
        }
    }

    private boolean isHost(org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor, WatchPartyService.WatchRoomRuntime runtime) {
        if (runtime == null || headerAccessor.getUser() == null || runtime.getHostUserId() == null) return false;
        return String.valueOf(runtime.getHostUserId()).equals(headerAccessor.getUser().getName());
    }

    @MessageMapping("/party/{roomId}/admin/approve")
    public void approveUser(@DestinationVariable String roomId, @Payload Map<String, String> payload, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            String targetSessionId = payload.get("sessionId");
            if(partyService.approveMember(roomId, targetSessionId)){
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/approval/" + targetSessionId, "APPROVED");
                
                // Broadcast member joined
                RoomMember approvedMember = runtime.getMembers().get(targetSessionId);
                if (approvedMember != null) {
                    Map<String, Object> joinMsg = new HashMap<>();
                    joinMsg.put("type", "MEMBER_JOINED");
                    joinMsg.put("sessionId", targetSessionId);
                    joinMsg.put("userName", approvedMember.getUserName());
                    messagingTemplate.convertAndSend("/topic/party/" + roomId + "/system", joinMsg);
                }
            }
        }
    }

    @MessageMapping("/party/{roomId}/admin/kick")
    public void kickUser(@DestinationVariable String roomId, @Payload Map<String, String> payload, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            String targetSessionId = payload.get("sessionId");
            partyService.kickMember(roomId, targetSessionId);
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/kick/" + targetSessionId, "KICKED");
        }
    }

    @MessageMapping("/party/{roomId}/admin/reject")
    public void rejectUser(@DestinationVariable String roomId, @Payload Map<String, String> payload, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            String targetSessionId = payload.get("sessionId");
            if (partyService.rejectMember(roomId, targetSessionId)) {
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/approval/" + targetSessionId, "REJECTED");
            }
        }
    }

    @MessageMapping("/party/{roomId}/admin/close")
    public void closeRoomStomp(@DestinationVariable String roomId, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            Map<String, Object> closeMsg = Map.of("type", "ROOM_CLOSED", "message", "Chủ phòng đã đóng phòng chiếu.");
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/system", closeMsg);
            partyService.closeRoom(roomId);
        }
    }

    @MessageMapping("/party/{roomId}/waitingList")
    public void getWaitingList(@DestinationVariable String roomId, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/waitingUpdate", 
                runtime.getWaitingList().values());
        }
    }
    
    @MessageMapping("/party/{roomId}/sync")
    public void syncPlayer(@DestinationVariable String roomId, @Payload Map<String, Object> action, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            if (action.containsKey("type")) {
                String type = (String) action.get("type");
                if ("PLAY".equals(type) || "PAUSE".equals(type)) {
                    runtime.setPlaybackStatus(type);
                } else if (action.containsKey("playbackStatus")) {
                    runtime.setPlaybackStatus((String) action.get("playbackStatus"));
                }
            }
            if (action.containsKey("currentTime")) {
                Object ct = action.get("currentTime");
                if (ct instanceof Number) {
                    runtime.setCurrentPlaybackTime(((Number) ct).doubleValue());
                }
            }
            runtime.setLastSyncTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/sync", action);
        }
    }
    
    @MessageMapping("/party/{roomId}/changeMovie")
    public void changeMovie(@DestinationVariable String roomId, @Payload Map<String, Object> movieData, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (isHost(headerAccessor, runtime)) {
            runtime.setCurrentMovieId((Integer) movieData.get("id"));
            runtime.setCurrentMovieTitle((String) movieData.get("title"));
            runtime.setCurrentMovieUrl((String) movieData.get("url"));
            runtime.setCurrentMoviePoster((String) movieData.getOrDefault("poster", "/images/placeholder.jpg"));
            
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/loadMovie", movieData);
        }
    }
    
    @MessageMapping("/party/{roomId}/webrtc/register")
    public void registerPeerId(@DestinationVariable String roomId, @Payload Map<String, String> payload, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        String httpSessionId = (String) headerAccessor.getSessionAttributes().get("httpSessionId");
        String peerId = payload.get("peerId");
        if (httpSessionId != null && peerId != null) {
            WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
            if (runtime != null && runtime.getMembers().containsKey(httpSessionId)) {
                RoomMember member = runtime.getMembers().get(httpSessionId);
                member.setPeerId(peerId);
                
                // Broadcast that this member has registered their WebRTC peer ID
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "PEER_REGISTERED");
                msg.put("sessionId", httpSessionId);
                msg.put("userId", member.getUserId());
                msg.put("userName", member.getUserName());
                msg.put("peerId", peerId);
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/system", msg);
            }
        }
    }
}