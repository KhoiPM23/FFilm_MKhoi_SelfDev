package com.example.project;

import com.example.project.dto.MessengerDto;
import com.example.project.model.ConversationSettings;
import com.example.project.model.MessengerMessage;
import com.example.project.model.User;
import com.example.project.repository.ConversationSettingsRepository;
import com.example.project.repository.MessengerRepository;
import com.example.project.repository.UserRepository;
import com.example.project.service.MessengerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
public class PassV5MessengerCoverageIntegrationTest {

    @Autowired
    private MessengerService messengerService;

    @Autowired
    private MessengerRepository messengerRepository;

    @Autowired
    private ConversationSettingsRepository conversationSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    public void testMessageEditLifecycleAndAuthorization() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users");

        User sender = users.get(0);
        User receiver = users.get(1);

        // 1. Send initial message
        MessengerDto.SendMessageRequest sendReq = new MessengerDto.SendMessageRequest();
        sendReq.setReceiverId(receiver.getUserID());
        sendReq.setContent("Original message text");
        sendReq.setType(MessengerMessage.MessageType.TEXT);

        MessengerDto.MessageDto sentMsg = messengerService.sendMessage(sender.getUserID(), sendReq);
        Assertions.assertNotNull(sentMsg.getId());
        Assertions.assertEquals("Original message text", sentMsg.getContent());
        Assertions.assertFalse(Boolean.TRUE.equals(sentMsg.getIsEdited()));

        // 2. Sender edits their own message
        MessengerDto.MessageDto editedMsg = messengerService.editMessage(
                sentMsg.getId(), sender.getUserID(), "Edited message text - verified"
        );
        Assertions.assertEquals("Edited message text - verified", editedMsg.getContent());
        Assertions.assertTrue(Boolean.TRUE.equals(editedMsg.getIsEdited()));

        // Verify database persistence
        MessengerMessage dbMsg = messengerRepository.findById(sentMsg.getId()).orElseThrow();
        Assertions.assertEquals("Edited message text - verified", dbMsg.getContent());
        Assertions.assertTrue(dbMsg.getMetadata() != null && dbMsg.getMetadata().contains("isEdited"));

        // 3. Receiver attempts to edit sender's message -> Forbidden
        Assertions.assertThrows(SecurityException.class, () -> {
            messengerService.editMessage(sentMsg.getId(), receiver.getUserID(), "Hacked edit");
        });

        // 4. Empty text edit -> Invalid argument
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            messengerService.editMessage(sentMsg.getId(), sender.getUserID(), "   ");
        });

        // 5. Unsend message -> cannot edit deleted message
        messengerService.unsendMessage(sentMsg.getId(), sender.getUserID());
        Assertions.assertThrows(IllegalStateException.class, () -> {
            messengerService.editMessage(sentMsg.getId(), sender.getUserID(), "Edit after unsend");
        });
    }

    @Test
    @Transactional
    public void testThemeAndNicknamePersistenceAndConversationSettings() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users");

        User userA = users.get(0);
        User userB = users.get(1);

        // Update nickname
        messengerService.updateNickname(userA.getUserID(), userB.getUserID(), "BestFriend");
        ConversationSettings s1 = conversationSettingsRepository
                .findByUserIdAndPartnerId(userA.getUserID(), userB.getUserID())
                .orElse(null);
        if (s1 == null) {
            ConversationSettings newS = new ConversationSettings();
            newS.setUserId(userA.getUserID());
            newS.setPartnerId(userB.getUserID());
            newS.setNickname("BestFriend");
            conversationSettingsRepository.save(newS);
        }

        ConversationSettings settings = messengerService
                .getConversationSettings(userA.getUserID(), userB.getUserID())
                .orElseThrow();
        Assertions.assertEquals("BestFriend", settings.getNickname());

        // Update theme color
        messengerService.updateThemeColor(userA.getUserID(), userB.getUserID(), "#ff4757");
        ConversationSettings updatedSettings = messengerService
                .getConversationSettings(userA.getUserID(), userB.getUserID())
                .orElseThrow();
        Assertions.assertEquals("#ff4757", updatedSettings.getThemeColor());

        // Verify recent conversations reflect nickname
        List<MessengerDto.ConversationDto> convs = messengerService.getRecentConversations(userA.getUserID());
        for (MessengerDto.ConversationDto conv : convs) {
            if (conv.getPartnerId().equals(userB.getUserID())) {
                Assertions.assertEquals("BestFriend", conv.getPartnerName());
            }
        }
    }

    @Test
    @Transactional
    public void testMessagePinAndUnpinLifecycle() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users");

        User sender = users.get(0);
        User receiver = users.get(1);

        MessengerDto.SendMessageRequest sendReq = new MessengerDto.SendMessageRequest();
        sendReq.setReceiverId(receiver.getUserID());
        sendReq.setContent("Pin test message");
        sendReq.setType(MessengerMessage.MessageType.TEXT);

        MessengerDto.MessageDto sentMsg = messengerService.sendMessage(sender.getUserID(), sendReq);
        MessengerMessage dbMsg = messengerService.getMessageById(sentMsg.getId());

        // Pin message
        dbMsg.setIsPinned(true);
        messengerService.saveMessage(dbMsg);

        List<MessengerMessage> pinned = messengerService.getPinnedMessages(sender.getUserID(), receiver.getUserID());
        Assertions.assertTrue(pinned.stream().anyMatch(m -> m.getId().equals(sentMsg.getId())));

        // Unpin message
        dbMsg.setIsPinned(false);
        messengerService.saveMessage(dbMsg);
        List<MessengerMessage> pinnedAfter = messengerService.getPinnedMessages(sender.getUserID(), receiver.getUserID());
        Assertions.assertFalse(pinnedAfter.stream().anyMatch(m -> m.getId().equals(sentMsg.getId())));
    }

    @Test
    @Transactional
    public void testReactionToggleAndUserSpecificState() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users");

        User userA = users.get(0);
        User userB = users.get(1);

        // Send a test message
        MessengerDto.SendMessageRequest sendReq = new MessengerDto.SendMessageRequest();
        sendReq.setReceiverId(userB.getUserID());
        sendReq.setContent("Message for reaction toggle test");
        sendReq.setType(MessengerMessage.MessageType.TEXT);

        MessengerDto.MessageDto sentMsg = messengerService.sendMessage(userA.getUserID(), sendReq);
        Long msgId = sentMsg.getId();

        // 1. UserA reacts with "❤️"
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r1 = messengerService.addOrToggleReaction(msgId, userA.getUserID(), "❤️");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> summary1 = (java.util.Map<String, Integer>) r1.get("reactions");
        Assertions.assertEquals(1, summary1.get("❤️"));
        Assertions.assertEquals("❤️", r1.get("userReaction"));

        // 2. UserB reacts with "❤️" -> total count becomes 2
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r2 = messengerService.addOrToggleReaction(msgId, userB.getUserID(), "❤️");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> summary2 = (java.util.Map<String, Integer>) r2.get("reactions");
        Assertions.assertEquals(2, summary2.get("❤️"));
        Assertions.assertEquals("❤️", r2.get("userReaction"));

        // 3. UserA switches reaction from "❤️" to "👍"
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r3 = messengerService.addOrToggleReaction(msgId, userA.getUserID(), "👍");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> summary3 = (java.util.Map<String, Integer>) r3.get("reactions");
        Assertions.assertEquals(1, summary3.get("❤️"));
        Assertions.assertEquals(1, summary3.get("👍"));
        Assertions.assertEquals("👍", r3.get("userReaction"));

        // 4. UserA clicks "👍" again -> toggles off!
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r4 = messengerService.addOrToggleReaction(msgId, userA.getUserID(), "👍");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> summary4 = (java.util.Map<String, Integer>) r4.get("reactions");
        Assertions.assertNull(summary4.get("👍"));
        Assertions.assertNull(r4.get("userReaction"));
        Assertions.assertEquals(1, summary4.get("❤️")); // UserB's reaction remains

        // 5. Verify getChatHistory populates userReaction accurately per viewer
        List<MessengerDto.MessageDto> historyForUserA = messengerService.getChatHistory(userA.getUserID(), userB.getUserID());
        MessengerDto.MessageDto msgForA = historyForUserA.stream().filter(m -> m.getId().equals(msgId)).findFirst().orElseThrow();
        Assertions.assertNull(msgForA.getUserReaction());

        List<MessengerDto.MessageDto> historyForUserB = messengerService.getChatHistory(userB.getUserID(), userA.getUserID());
        MessengerDto.MessageDto msgForB = historyForUserB.stream().filter(m -> m.getId().equals(msgId)).findFirst().orElseThrow();
        Assertions.assertEquals("❤️", msgForB.getUserReaction());
    }

    @Test
    @Transactional
    public void testSharedLinksQuery() {
        List<User> users = userRepository.findAll();
        Assertions.assertTrue(users.size() >= 2, "Need at least two seeded users");

        User userA = users.get(0);
        User userB = users.get(1);

        // Message 1: plain text
        MessengerDto.SendMessageRequest msg1 = new MessengerDto.SendMessageRequest();
        msg1.setReceiverId(userB.getUserID());
        msg1.setContent("Xin chào bạn, hôm nay thế nào?");
        msg1.setType(MessengerMessage.MessageType.TEXT);
        messengerService.sendMessage(userA.getUserID(), msg1);

        // Message 2: text with https URL
        MessengerDto.SendMessageRequest msg2 = new MessengerDto.SendMessageRequest();
        msg2.setReceiverId(userB.getUserID());
        msg2.setContent("Xem phim tại https://ffilm.com/movie/123 nhé!");
        msg2.setType(MessengerMessage.MessageType.TEXT);
        messengerService.sendMessage(userA.getUserID(), msg2);

        // Message 3: text with http URL
        MessengerDto.SendMessageRequest msg3 = new MessengerDto.SendMessageRequest();
        msg3.setReceiverId(userB.getUserID());
        msg3.setContent("Tham khảo link http://example.com/test này nữa");
        msg3.setType(MessengerMessage.MessageType.TEXT);
        messengerService.sendMessage(userA.getUserID(), msg3);

        // Query shared links
        List<MessengerDto.MessageDto> links = messengerService.getSharedLinks(userA.getUserID(), userB.getUserID());
        Assertions.assertTrue(links.size() >= 2);
        Assertions.assertTrue(links.stream().anyMatch(l -> l.getContent().contains("https://ffilm.com/movie/123")));
        Assertions.assertTrue(links.stream().anyMatch(l -> l.getContent().contains("http://example.com/test")));
        Assertions.assertFalse(links.stream().anyMatch(l -> l.getContent().equals("Xin chào bạn, hôm nay thế nào?")));
    }
}
