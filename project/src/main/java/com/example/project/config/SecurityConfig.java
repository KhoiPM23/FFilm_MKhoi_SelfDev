package com.example.project.config;

import org.springframework.beans.factory.annotation.Autowired; // <-- THÊM IMPORT NÀY
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // <-- THÊM IMPORT NÀY

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // === THÊM DÒNG NÀY ===
    // Tiêm (Inject) cái filter chúng ta vừa tạo
    @Autowired
    private CustomSessionAuthFilter customSessionAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            
            .authorizeHttpRequests(authorize -> authorize
                // Các rule bảo vệ của chúng ta vẫn giữ nguyên
                .requestMatchers("/history").authenticated()
                .requestMatchers("/api/history/**").authenticated()
                .requestMatchers("/**").permitAll()
            )
            
            // Cấu hình chuyển hướng về /login vẫn giữ nguyên
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/") 
                .logoutUrl("/logout") 
                .invalidateHttpSession(true) 
                .deleteCookies("JSESSIONID")
            );
            
        // === THÊM DÒNG QUAN TRỌNG NÀY ===
        // Bảo Spring Security chạy Filter của chúng ta TRƯỚC khi
        // nó chạy filter kiểm tra Username/Password chuẩn
        http.addFilterBefore(customSessionAuthFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}