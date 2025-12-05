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

@Controller
public class WatchPartyController {

    @Autowired private WatchPartyService partyService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // --- HELPER: Lấy User từ Session (Fix lỗi đăng nhập) ---
    private UserSessionDto getUserFromSession(HttpSession session) {
        if (session.getAttribute("user") != null) return (UserSessionDto) session.getAttribute("user");
        if (session.getAttribute("admin") != null) return (UserSessionDto) session.getAttribute("admin");
        if (session.getAttribute("moderator") != null) return (UserSessionDto) session.getAttribute("moderator");
        if (session.getAttribute("contentManager") != null) return (UserSessionDto) session.getAttribute("contentManager");
        return null;
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
                     session.getId(), user.getId(), user.getUserName(), null, true, false
                 );
                 partyService.startRoom(runtimeId, hostMember);
                 runtime = partyService.getRuntimeRoom(runtimeId);
             } else {
                 return "redirect:/watch-party?error=room_not_active";
             }
        }

        model.addAttribute("room", dbRoom);
        model.addAttribute("user", user);
        
        // Check quyền Host
        boolean isHost = dbRoom.getOwner().getUserID() == user.getId();
        model.addAttribute("isHost", isHost);
        
        // Check trạng thái Join (Waiting/Joined)
        String joinStatus = "JOINED";
        if (!isHost && "PRIVATE".equals(dbRoom.getAccessType())) {
             // Logic check waiting list sẽ nằm ở JS socket connection
             joinStatus = "WAITING"; 
        }
        model.addAttribute("joinStatus", joinStatus); 
        
        return "watch-party/room";
    }

    // --- WEBSOCKET HANDLERS ---

    @MessageMapping("/party/{roomId}/chat")
    public void chat(@DestinationVariable String roomId, @Payload SocketMessage msg) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (runtime != null) {
            // Service tự động set ID + timestamp
            msg.setTimestamp(java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            runtime.addChat(msg);
        }
        messagingTemplate.convertAndSend("/topic/party/" + roomId + "/chat", msg);
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
                Map<String, Object> movieData = Map.of(
                    "id", runtime.getCurrentMovieId() != null ? runtime.getCurrentMovieId() : 0,
                    "title", runtime.getCurrentMovieTitle() != null ? runtime.getCurrentMovieTitle() : "",
                    "url", runtime.getCurrentMovieUrl()
                );
                messagingTemplate.convertAndSend("/topic/party/" + roomId + "/loadMovie", movieData);
            }
        }
    }

    @MessageMapping("/party/{roomId}/admin/approve")
    public void approveUser(@DestinationVariable String roomId, @Payload Map<String, String> payload) {
        String targetSessionId = payload.get("sessionId");
        if(partyService.approveMember(roomId, targetSessionId)){
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/approval/" + targetSessionId, "APPROVED");
        }
    }

    @MessageMapping("/party/{roomId}/admin/kick")
    public void kickUser(@DestinationVariable String roomId, @Payload Map<String, String> payload) {
        String targetSessionId = payload.get("sessionId");
        partyService.kickMember(roomId, targetSessionId);
        messagingTemplate.convertAndSend("/topic/party/" + roomId + "/kick/" + targetSessionId, "KICKED");
    }

    @MessageMapping("/party/{roomId}/waitingList")
    public void getWaitingList(@DestinationVariable String roomId) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (runtime != null) {
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/waitingUpdate", 
                runtime.getWaitingList().values());
        }
    }
    
    @MessageMapping("/party/{roomId}/sync")
    public void syncPlayer(@DestinationVariable String roomId, @Payload Map<String, Object> action) {
        messagingTemplate.convertAndSend("/topic/party/" + roomId + "/sync", action);
    }
    
    @MessageMapping("/party/{roomId}/changeMovie")
    public void changeMovie(@DestinationVariable String roomId, @Payload Map<String, Object> movieData) {
        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(roomId);
        if (runtime != null) {
            runtime.setCurrentMovieId((Integer) movieData.get("id"));
            runtime.setCurrentMovieTitle((String) movieData.get("title"));
            runtime.setCurrentMovieUrl((String) movieData.get("url"));
            runtime.setCurrentMoviePoster((String) movieData.getOrDefault("poster", "/images/placeholder.jpg"));
            
            messagingTemplate.convertAndSend("/topic/party/" + roomId + "/loadMovie", movieData);
        }
    }
}