package com.example.project;

import com.example.project.config.OAuth2LoginSuccessHandler;
import com.example.project.dto.UserSessionDto;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(userRepository, passwordEncoder);
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }

    @Test
    void testNewUserRegistrationViaGoogle() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn("newuser@gmail.com");
        when(oAuth2User.getAttribute("name")).thenReturn("New User");
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedpassword");
        when(request.getSession(true)).thenReturn(session);

        User savedUser = new User();
        savedUser.setUserID(100);
        savedUser.setUserName("New User");
        savedUser.setEmail("newuser@gmail.com");
        savedUser.setRole("USER");
        savedUser.setStatus(true);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals("newuser@gmail.com", created.getEmail());
        assertEquals("New User", created.getUserName());
        assertEquals("USER", created.getRole());
        assertTrue(created.isStatus());
        assertEquals("0000000000", created.getPhoneNumber());

        verify(session).setAttribute(eq("user"), any(UserSessionDto.class));
        verify(response).sendRedirect("/");
    }

    @Test
    void testExistingUserLoginWithPrevUrlRedirect() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn("existing@gmail.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Existing User");

        User existingUser = new User();
        existingUser.setUserID(55);
        existingUser.setUserName("Existing User");
        existingUser.setEmail("existing@gmail.com");
        existingUser.setRole("USER");
        existingUser.setStatus(true);

        when(userRepository.findByEmail("existing@gmail.com")).thenReturn(Optional.of(existingUser));
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("PREV_URL")).thenReturn("/movie/detail/123");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository, never()).save(any(User.class));
        verify(session).removeAttribute("PREV_URL");
        verify(session).setAttribute(eq("user"), any(UserSessionDto.class));
        verify(response).sendRedirect("/movie/detail/123");
    }

    @Test
    void testLockedUserRedirectsToError() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn("locked@gmail.com");

        User lockedUser = new User();
        lockedUser.setUserID(99);
        lockedUser.setEmail("locked@gmail.com");
        lockedUser.setStatus(false); // Locked account

        when(userRepository.findByEmail("locked@gmail.com")).thenReturn(Optional.of(lockedUser));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/login?error=locked");
        verify(request, never()).getSession(anyBoolean());
    }

    @Test
    void testMissingEmailRedirectsToOAuthError() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/login?error=oauth2");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void testUnverifiedEmailRedirectsToOAuthError() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn("fake@gmail.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Fake User");
        when(oAuth2User.getAttribute("email_verified")).thenReturn(false);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/login?error=oauth2");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
