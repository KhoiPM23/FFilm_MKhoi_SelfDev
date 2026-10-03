package com.example.project;

import com.example.project.dto.UserSessionDto;
import com.example.project.repository.AIChatHistoryRepository;
import com.example.project.service.AIAgentService;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Acceptance tests for AI chat history isolation (guest vs account, user vs user).
 * Tests assert the DESIRED behavior; they seed rows via the service layer (no LLM call)
 * and exercise the real HTTP endpoints through MockMvc.
 * Uses synthetic userIds/conversationIds and cleans up after itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AIHistoryIsolationTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired private MockMvc mvc;
    @Autowired private AIAgentService service;
    @Autowired private AIChatHistoryRepository repo;

    private final List<String> createdCids = new ArrayList<>();

    private String newCid() {
        String c = "iso-" + UUID.randomUUID();
        createdCids.add(c);
        return c;
    }

    @AfterEach
    void cleanup() {
        createdCids.forEach(repo::deleteBySessionId);
        repo.deleteByUserId(USER_A);
        repo.deleteByUserId(USER_B);
    }

    private MockHttpSession loginAs(int userId) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("user", new UserSessionDto(userId, "u" + userId, "u" + userId + "@test.local", "ROLE_USER"));
        return s;
    }

    private MockHttpSession guest() {
        return new MockHttpSession();
    }

    private int historySize(MockHttpSession s, String cid) throws Exception {
        String body = mvc.perform(get("/api/ai-agent/history").session(s).param("conversationId", cid))
                .andReturn().getResponse().getContentAsString();
        return new JSONArray(body).length();
    }

    private void deleteHistory(MockHttpSession s, String cid) throws Exception {
        mvc.perform(delete("/api/ai-agent/history").session(s).param("conversationId", cid));
    }

    @Test
    @DisplayName("H1: user A sees own history")
    void h1_userSeesOwnHistory() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "hi", "hello", null);
        assertThat(historySize(loginAs(USER_A), c)).isEqualTo(2);
    }

    @Test
    @DisplayName("H2: user B cannot see user A history")
    void h2_userBCannotSeeA() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "secret-A", "reply-A", null);
        assertThat(historySize(loginAs(USER_B), c)).isZero();
    }

    @Test
    @DisplayName("H3: guest sees own history")
    void h3_guestSeesOwnHistory() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, null, "hi", "hello", null);
        assertThat(historySize(guest(), c)).isEqualTo(2);
    }

    @Test
    @DisplayName("H5: after logout, guest (same tab => same conversationId) cannot see account history")
    void h5_guestCannotSeeAccountHistoryAfterLogout() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "secret-A", "reply-A", null);
        // Logout invalidates the HttpSession but the browser keeps sessionStorage cid => same cid, no user
        assertThat(historySize(guest(), c)).isZero();
    }

    @Test
    @DisplayName("H7: authenticated delete persists in DB")
    void h7_deletePersists() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "hi", "hello", null);
        deleteHistory(loginAs(USER_A), c);
        assertThat(repo.findByUserIdOrderByTimestampAsc(USER_A)).isEmpty();
        assertThat(historySize(loginAs(USER_A), c)).isZero();
    }

    @Test
    @DisplayName("H7b: guest delete persists in DB")
    void h7b_guestDeletePersists() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, null, "hi", "hello", null);
        deleteHistory(guest(), c);
        assertThat(repo.findBySessionIdAndUserIdIsNullOrderByTimestampAsc(c)).isEmpty();
    }

    @Test
    @DisplayName("H8a: user B cannot delete user A conversation by passing A's conversationId")
    void h8a_userBCannotDeleteA() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "hi", "hello", null);
        deleteHistory(loginAs(USER_B), c);
        assertThat(repo.findByUserIdOrderByTimestampAsc(USER_A)).hasSize(2);
    }

    @Test
    @DisplayName("H8b: guest cannot delete an account conversation by passing its conversationId")
    void h8b_guestCannotDeleteAccountConversation() throws Exception {
        String c = newCid();
        service.saveChatHistory(c, USER_A, "hi", "hello", null);
        deleteHistory(guest(), c);
        assertThat(repo.findByUserIdOrderByTimestampAsc(USER_A)).hasSize(2);
    }

    @Test
    @DisplayName("H8d: authenticated delete wipes ALL history for that user (D2: merged timeline)")
    void h8d_authenticatedDeleteWipesAllUserHistory() throws Exception {
        String c1 = newCid();
        String c2 = newCid();
        service.saveChatHistory(c1, USER_A, "chat-1", "r1", null);
        service.saveChatHistory(c2, USER_A, "chat-2", "r2", null);
        // Delete using USER_A credentials — D2: wipes entire user's history
        deleteHistory(loginAs(USER_A), c1);
        // Both conversations should now be empty
        assertThat(repo.findByUserIdOrderByTimestampAsc(USER_A)).isEmpty();
    }

    @Test
    @DisplayName("H10: stale/unknown conversationId returns empty safely")
    void h10_staleCidSafe() throws Exception {
        assertThat(historySize(guest(), "does-not-exist-" + UUID.randomUUID())).isZero();
    }
}
