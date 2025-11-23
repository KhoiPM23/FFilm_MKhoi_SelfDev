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
                // === BỔ SUNG CÁC RULE BẢO VỆ ADMIN ===
                .requestMatchers("/api/admin/**", "/admin/**", "/manage-account").hasRole("ADMIN")
                .requestMatchers("/api/comments/admin/**").hasRole("ADMIN")  // Thêm dòng này
                .requestMatchers("/api/content/**", "/manage-movies", "/ContentManagerScreen/**").hasAnyRole("ADMIN", "CONTENT_MANAGER")
                .requestMatchers("/ModeratorScreen/**").hasAnyRole("ADMIN", "MODERATOR")
                // ======================================
                
                // Các rule cũ của bạn
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