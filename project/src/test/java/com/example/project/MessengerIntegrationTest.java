package com.example.project;

import com.example.project.dto.MessengerDto;
import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import com.example.project.repository.FriendRequestRepository;
import com.example.project.repository.MessengerRepository;
import com.example.project.repository.UserRepository;
import com.example.project.service.MessengerService;
import com.example.project.service.OnlineStatusService;
import com.example.project.service.SocialService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class MessengerIntegrationTest {

    @Autowired
    private MessengerService messengerService;

    @Autowired
    private MessengerRepository messengerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private SocialService socialService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Test
    @Transactional
    public void testSendMessageAndReactionPersistence() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users to run messenger test");

        User sender = users.get(0);
        User receiver = users.get(1);

        // 1. Send message
        MessengerDto.SendMessageRequest req = new MessengerDto.SendMessageRequest();
        req.setReceiverId(receiver.getUserID());
        req.setContent("Hello Realtime Messenger!");
        req.setType(MessengerMessage.MessageType.TEXT);

        MessengerDto.MessageDto sent = messengerService.sendMessage(sender.getUserID(), req);
        Assertions.assertNotNull(sent);
        Assertions.assertNotNull(sent.getId());
        Assertions.assertEquals(sender.getUserID(), sent.getSenderId());
        Assertions.assertEquals(receiver.getUserID(), sent.getReceiverId());
        Assertions.assertEquals("Hello Realtime Messenger!", sent.getContent());

        // 2. Add and persist reaction
        Map<String, Object> reactionRes = messengerService.addOrToggleReaction(sent.getId(), sender.getUserID(), "❤️");
        Assertions.assertNotNull(reactionRes);
        @SuppressWarnings("unchecked")
        Map<String, Integer> reactions = (Map<String, Integer>) reactionRes.get("reactions");
        Assertions.assertNotNull(reactions);
        Assertions.assertTrue(reactions.containsKey("❤️"));
        Assertions.assertEquals(1, reactions.get("❤️"));
        Assertions.assertEquals("❤️", reactionRes.get("userReaction"));

        // Verify metadata persistence in DB
        MessengerMessage savedMsg = messengerRepository.findById(sent.getId()).orElse(null);
        Assertions.assertNotNull(savedMsg);
        Assertions.assertNotNull(savedMsg.getMetadata());
        Assertions.assertTrue(savedMsg.getMetadata().contains("❤️"));

        // 3. Test Pinning
        savedMsg.setIsPinned(true);
        messengerService.saveMessage(savedMsg);

        List<MessengerMessage> pinned = messengerService.getPinnedMessages(sender.getUserID(), receiver.getUserID());
        Assertions.assertNotNull(pinned);
        Assertions.assertTrue(pinned.stream().anyMatch(m -> m.getId().equals(sent.getId())));

        // 4. Test Chat History retrieval
        List<MessengerDto.MessageDto> history = messengerService.getChatHistory(sender.getUserID(), receiver.getUserID());
        Assertions.assertNotNull(history);
        Assertions.assertFalse(history.isEmpty());
        MessengerDto.MessageDto retrieved = history.stream().filter(m -> m.getId().equals(sent.getId())).findFirst().orElse(null);
        Assertions.assertNotNull(retrieved);
        Assertions.assertNotNull(retrieved.getReactions());
        Assertions.assertEquals(1, retrieved.getReactions().get("❤️"));
        Assertions.assertTrue(retrieved.getIsPinned());
    }

    @Test
    public void testOnlineStatusTracking() {
        Integer testUserId = 99999;
        
        // Initially offline
        onlineStatusService.markOffline(testUserId);
        Assertions.assertFalse(onlineStatusService.isOnline(testUserId));

        // Mark online
        onlineStatusService.markOnline(testUserId);
        Assertions.assertTrue(onlineStatusService.isOnline(testUserId));

        // Mark offline
        onlineStatusService.markOffline(testUserId);
        Assertions.assertFalse(onlineStatusService.isOnline(testUserId));
    }

    @Test
    @Transactional
    public void testRecordCallMessageAndHistoryPersistence() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users to run call history test");

        User caller = users.get(0);
        User callee = users.get(1);

        String testCallId = "call_test_" + System.currentTimeMillis();

        // 1. Record completed video call
        MessengerDto.MessageDto callMsg = messengerService.recordCallMessage(
                caller.getUserID(), callee.getUserID(), "VIDEO", "COMPLETED", 154, testCallId
        );

        Assertions.assertNotNull(callMsg);
        Assertions.assertNotNull(callMsg.getId());
        Assertions.assertEquals(MessengerMessage.MessageType.CALL_END, callMsg.getType());
        Assertions.assertEquals("VIDEO", callMsg.getMediaUrl());
        Assertions.assertEquals(154, callMsg.getCallDuration());
        Assertions.assertEquals("COMPLETED", callMsg.getCallStatus());
        Assertions.assertEquals(caller.getUserID(), callMsg.getSenderId());
        Assertions.assertEquals(callee.getUserID(), callMsg.getReceiverId());

        // 2. Test Idempotency: Duplicate record with same callId returns the existing message without creating a duplicate
        MessengerDto.MessageDto duplicateCallMsg = messengerService.recordCallMessage(
                caller.getUserID(), callee.getUserID(), "VIDEO", "COMPLETED", 154, testCallId
        );
        Assertions.assertEquals(callMsg.getId(), duplicateCallMsg.getId(), "Must return same record to prevent duplicate entries");

        // 3. Verify history retrieval returns the call event
        List<MessengerDto.MessageDto> history = messengerService.getChatHistory(caller.getUserID(), callee.getUserID());
        Assertions.assertNotNull(history);
        MessengerDto.MessageDto found = history.stream()
                .filter(m -> m.getId().equals(callMsg.getId()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(found, "Call event must be present in conversation history");
        Assertions.assertEquals(154, found.getCallDuration());
        Assertions.assertEquals("COMPLETED", found.getCallStatus());

        // 4. Record missed voice call
        String missedCallId = "call_missed_" + System.currentTimeMillis();
        MessengerDto.MessageDto missedCall = messengerService.recordCallMessage(
                caller.getUserID(), callee.getUserID(), "AUDIO", "MISSED", 0, missedCallId
        );
        Assertions.assertNotNull(missedCall);
        Assertions.assertEquals("MISSED", missedCall.getCallStatus());
        Assertions.assertEquals(0, missedCall.getCallDuration());
        Assertions.assertEquals("AUDIO", missedCall.getMediaUrl());
        Assertions.assertTrue(missedCall.getContent().contains("nhỡ"));
    }
}

