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
@Component // Rất quan trọng: Đánh dấu đây là một Spring Bean
public class CustomSessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false); // false = không tạo session mới nếu chưa có
        UserSessionDto userSession = null;

        if (session != null) {
            // Kiểm tra tất cả các key session có thể có mà bạn đã định nghĩa
            if (session.getAttribute("user") != null) {
                userSession = (UserSessionDto) session.getAttribute("user");
            } else if (session.getAttribute("admin") != null) {
                userSession = (UserSessionDto) session.getAttribute("admin");
            } else if (session.getAttribute("contentManager") != null) {
                userSession = (UserSessionDto) session.getAttribute("contentManager");
            } else if (session.getAttribute("moderator") != null) {
                userSession = (UserSessionDto) session.getAttribute("moderator");
            }
        }

        // Nếu có user trong session VÀ Spring Security chưa biết (Context đang trống)
        if (userSession != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 1. Tạo quyền (Authority) từ role trong DTO
            // Chúng ta thêm tiền tố "ROLE_" vì Spring Security thường yêu cầu
            String role = userSession.getRole() != null ? userSession.getRole().toUpperCase() : "USER";
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            // 2. Tạo UserDetails (đối tượng user của Spring Security)
            // Mật khẩu có thể để trống vì ta không check pass ở đây
            UserDetails userDetails = new User(userSession.getEmail(), "", authorities);

            // 3. Tạo đối tượng Authentication
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 4. Đặt đối tượng Authentication vào SecurityContext
            // Đây là bước "báo" cho Spring Security biết user đã đăng nhập
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Chuyển request cho filter tiếp theo trong chuỗi
        filterChain.doFilter(request, response);
    }
}