package com.example.project.config;

import com.example.project.dto.UserSessionDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter này đọc UserSessionDto từ HttpSession (do UserAuthenticationController
 * tạo)
 * và chuyển đổi nó thành đối tượng Authentication của Spring Security.
 * Điều này cho phép .authenticated() hoạt động chính xác.
 */
@Component
public class CustomSessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false); 
        UserSessionDto userSession = null;

        if (session != null) {
            // --- SỬA ĐỔI: THAY ĐỔI THỨ TỰ ƯU TIÊN ---
            // Kiểm tra quyền cao nhất trước để tránh bị nhận diện nhầm là user thường
            if (session.getAttribute("admin") != null) {
                userSession = (UserSessionDto) session.getAttribute("admin");
            } else if (session.getAttribute("contentManager") != null) {
                userSession = (UserSessionDto) session.getAttribute("contentManager");
            } else if (session.getAttribute("moderator") != null) {
                userSession = (UserSessionDto) session.getAttribute("moderator");
            } else if (session.getAttribute("user") != null) {
                // Kiểm tra user cuối cùng
                userSession = (UserSessionDto) session.getAttribute("user");
            }
        }

        // ... phần logic set Authentication giữ nguyên
        if (userSession != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = userSession.getRole() != null ? userSession.getRole().toUpperCase() : "USER";
            // Lưu ý: Đảm bảo role trong DB khớp với hasRole trong SecurityConfig
            // Ví dụ: DB lưu "ADMIN" -> Authority thành "ROLE_ADMIN"
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            UserDetails userDetails = new User(userSession.getEmail(), "", authorities);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
