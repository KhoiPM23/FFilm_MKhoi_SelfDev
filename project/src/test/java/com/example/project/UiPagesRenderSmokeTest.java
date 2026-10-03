package com.example.project;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Smoke test: các trang UI vừa chỉnh phải render được (cú pháp Thymeleaf/fragment đúng). */
@SpringBootTest
@AutoConfigureMockMvc
class UiPagesRenderSmokeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private MockHttpSession sessionOfFirstUser() {
        User u = userRepository.findAll().stream().filter(User::isStatus).findFirst().orElseThrow();
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("user", new UserSessionDto(u.getUserID(), u.getUserName(), u.getEmail(), u.getRole()));
        return s;
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url).session(sessionOfFirstUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void lobbyRendersTabsAndLinksToMyRooms() throws Exception {
        String html = body("/watch-party");
        assertTrue(html.contains("wp-tabs"));
        assertTrue(html.contains("href=\"/my-rooms\""));
        assertFalse(html.matches("(?s).*\\sth:(text|href|each|if|replace|onclick|style|classappend)=.*"),
                "Thymeleaf attribute chưa được xử lý");
    }

    @Test
    void myRoomsRendersTabsAndLinksToLobby() throws Exception {
        String html = body("/my-rooms");
        assertTrue(html.contains("wp-tabs"));
        assertTrue(html.contains("href=\"/watch-party\""));
        assertTrue(html.contains("copyInvite") || html.contains("Xem các phòng đang mở"));
    }

    @Test
    void profileRendersPrivacySwitches() throws Exception {
        String html = body("/profile");
        assertTrue(html.contains("name=\"publicFriend\""));
        assertTrue(html.contains("name=\"publicFav\""));
        assertTrue(html.contains("name=\"publicHistory\""));
        assertTrue(html.contains("privacy-switch"));
    }
}
