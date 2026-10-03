package com.example.project.config;

import com.example.project.dto.UserSessionDto;
import com.example.project.model.User;
import com.example.project.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    public OAuth2LoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    public OAuth2LoginSuccessHandler(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User)) {
            response.sendRedirect("/login?error=oauth2");
            return;
        }

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            log.error("Google OAuth2 login failed: No email attribute returned");
            response.sendRedirect("/login?error=oauth2");
            return;
        }

        if (Boolean.FALSE.equals(oAuth2User.getAttribute("email_verified"))) {
            log.warn("Google OAuth2 login rejected: email not verified");
            response.sendRedirect("/login?error=oauth2");
            return;
        }

        email = email.trim().toLowerCase();

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (!user.isStatus()) {
                log.warn("Google OAuth2 login attempt for locked account: {}", email);
                response.sendRedirect("/login?error=locked");
                return;
            }
        } else {
            // New user registration via Google OAuth2
            user = new User();
            user.setEmail(email);
            user.setUserName(name != null && !name.isBlank() ? name.trim() : email.split("@")[0]);
            // Generate secure random hashed password for entity validation
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRole("USER");
            user.setStatus(true);
            user.setPhoneNumber("0000000000"); // Satisfy @NotBlank on phoneNumber
            user.setPublicFriendList(true);
            user.setPublicWatchHistory(true);
            user.setPublicFavorites(true);

            user = userRepository.save(user);
            log.info("Created new user via Google OAuth2: email={}, id={}", email, user.getUserID());
        }

        // Setup session state compatible with CustomSessionAuthFilter & FFilm features
        HttpSession session = request.getSession(true);
        UserSessionDto userSession = new UserSessionDto(
                user.getUserID(),
                user.getUserName(),
                user.getEmail(),
                user.getRole()
        );

        session.removeAttribute("user");
        session.removeAttribute("admin");
        session.removeAttribute("contentManager");
        session.removeAttribute("moderator");

        session.setAttribute("user", userSession);

        String roleLower = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if ("admin".equals(roleLower)) {
            session.setAttribute("admin", userSession);
        } else if ("content_manager".equals(roleLower) || "contentmanager".equals(roleLower)) {
            session.setAttribute("contentManager", userSession);
        } else if ("moderator".equals(roleLower)) {
            session.setAttribute("moderator", userSession);
        }

        // Determine destination redirect
        String redirectUrl = (String) session.getAttribute("PREV_URL");
        session.removeAttribute("PREV_URL");

        if (redirectUrl != null && isValidRedirectUrl(redirectUrl)) {
            response.sendRedirect(redirectUrl);
            return;
        }

        // Role-based default landing page
        if ("admin".equals(roleLower)) {
            response.sendRedirect("/AdminScreen/homeAdminManager");
        } else if ("content_manager".equals(roleLower) || "contentmanager".equals(roleLower)) {
            response.sendRedirect("/manage-movies");
        } else if ("moderator".equals(roleLower)) {
            response.sendRedirect("/moderator/chat");
        } else {
            response.sendRedirect("/");
        }
    }

    private boolean isValidRedirectUrl(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("/") || url.startsWith("//") || url.startsWith("/\\")) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("/login") || lower.startsWith("/register")
                || lower.startsWith("/logout") || lower.startsWith("/forgot")
                || lower.startsWith("/reset") || lower.startsWith("/verify")
                || lower.startsWith("/update-success")
                || lower.startsWith("/api/") || lower.startsWith("/css/")
                || lower.startsWith("/js/") || lower.startsWith("/images/")
                || lower.startsWith("/video/") || lower.contains("favicon")) {
            return false;
        }
        return true;
    }
}
