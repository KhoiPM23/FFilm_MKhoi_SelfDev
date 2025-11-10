package com.example.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/**").permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/") 
                // Vẫn giữ URL xử lý logout là /logout (mặc định)
                .logoutUrl("/logout") 
                // Vô hiệu hóa session của Spring Security
                .invalidateHttpSession(true) 
                // Xóa cookie phiên
                .deleteCookies("JSESSIONID")
            );
            
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}