package com.example.project;

import com.example.project.dto.RoomMember;
import com.example.project.dto.SocketMessage;
import com.example.project.service.WatchPartyService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class WatchPartyIntegrationTest {

    @Autowired
    private WatchPartyService partyService;

    @Autowired
    private com.example.project.repository.UserRepository userRepository;

    @Test
    public void testWatchPartyLifecycleAndSync() {
        java.util.List<com.example.project.model.User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users to run Watch Party test");

        com.example.project.model.User hostUser = users.get(0);
        com.example.project.model.User partUser = users.get(1);

        // 1. Create real room in DB
        com.example.project.model.WatchRoom dbRoom = partyService.createRoom(
            "Integration Test Room", 
            "PUBLIC", 
            null, 
            10, 
            hostUser.getUserID()
        );
        Assertions.assertNotNull(dbRoom);
        String testRoomId = String.valueOf(dbRoom.getId());

        // 2. Host starts runtime room
        RoomMember host = new RoomMember("session_host", hostUser.getUserID(), hostUser.getUserName(), null, null, false, false);
        partyService.startRoom(testRoomId, host);

        WatchPartyService.WatchRoomRuntime runtime = partyService.getRuntimeRoom(testRoomId);
        Assertions.assertNotNull(runtime);
        Assertions.assertEquals(hostUser.getUserID(), runtime.getHostUserId());
        Assertions.assertEquals("session_host", runtime.getHostSessionId());
        Assertions.assertEquals(1, runtime.getMembers().size());

        // 3. Participant joins public room
        RoomMember participant = new RoomMember("session_part1", partUser.getUserID(), partUser.getUserName(), null, null, false, false);
        String joinStatus = partyService.requestJoin(testRoomId, participant);
        Assertions.assertEquals("JOINED", joinStatus);
        Assertions.assertEquals(2, runtime.getMembers().size());

        // 4. Playback Sync and Late Join Calculation (Using Default Demo Video)
        runtime.setCurrentMovieId(1);
        runtime.setCurrentMovieTitle("Big Buck Bunny (Demo)");
        runtime.setCurrentMovieUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
        runtime.setPlaybackStatus("PLAY");
        runtime.setCurrentPlaybackTime(45.0);
        runtime.setLastSyncTimestamp(System.currentTimeMillis() - 5000); // 5 seconds ago

        double estimatedTime = runtime.getCurrentPlaybackTime();
        if ("PLAY".equals(runtime.getPlaybackStatus())) {
            estimatedTime += (System.currentTimeMillis() - runtime.getLastSyncTimestamp()) / 1000.0;
        }
        // Should be approximately 50.0 seconds (45 + 5s elapsed)
        Assertions.assertTrue(estimatedTime >= 49.0 && estimatedTime <= 52.0, "Estimated time should reflect elapsed playback time");

        // 5. Chat history in Watch Party
        SocketMessage chatMsg = new SocketMessage();
        chatMsg.setSender(partUser.getUserName());
        chatMsg.setContent("Tuyệt vời quá!");
        chatMsg.setType("CHAT");
        runtime.addChat(chatMsg);
        Assertions.assertFalse(runtime.getChatHistory().isEmpty());
        Assertions.assertEquals("Tuyệt vời quá!", runtime.getChatHistory().get(0).getContent());

        // 6. Host leaves -> Deterministic Host Migration to participant
        String migrationResult = partyService.handleDisconnect("session_host");
        Assertions.assertEquals("session_part1", migrationResult, "Host should migrate to remaining participant");
        Assertions.assertEquals("session_part1", runtime.getHostSessionId());
        Assertions.assertEquals(partUser.getUserID(), runtime.getHostUserId());
        Assertions.assertEquals(partUser.getUserName(), runtime.getHostName());

        // 7. Remaining participant leaves -> Room cleanup
        String finalDisconnect = partyService.handleDisconnect("session_part1");
        Assertions.assertNull(finalDisconnect);
        Assertions.assertNull(partyService.getRuntimeRoom(testRoomId), "Room should be cleaned up from RAM when empty");

        // 8. Delete room from DB
        partyService.deleteRoom(hostUser.getUserID(), dbRoom.getId());
    }
}
