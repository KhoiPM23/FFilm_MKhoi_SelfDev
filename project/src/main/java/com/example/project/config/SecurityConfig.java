package com.example.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomSessionAuthFilter customSessionAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            
            .authorizeHttpRequests(authorize -> authorize
                // === CẬP NHẬT RULE BẢO VỆ ===
                // Admin
                .requestMatchers("/api/admin/**", "/admin/**", "/manage-account").hasRole("ADMIN")
                
                // Content Manager (bao gồm cả /manage-movies và dashboard)
                .requestMatchers("/api/content/**", "/manage-movies", "/manage-banners", "/content/**").hasAnyRole("ADMIN", "CONTENT_MANAGER")
                
                // Moderator (bao gồm dashboard và các trang quản lý)
                .requestMatchers("/moderator/**", "/manage-comments", "/manage-chat").hasAnyRole("ADMIN", "MODERATOR")
                // ======================================
                
                .requestMatchers("/history", "/api/history/**", "/favorites/**").authenticated()
                .requestMatchers("/**").permitAll()
            )
            
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/") 
                .logoutUrl("/logout") 
                .invalidateHttpSession(true) 
                .deleteCookies("JSESSIONID")
            );
            
        http.addFilterBefore(customSessionAuthFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}