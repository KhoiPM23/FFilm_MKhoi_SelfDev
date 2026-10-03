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

    @Autowired(required = false)
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            
            .authorizeHttpRequests(authorize -> authorize
                // === BỔ SUNG CÁC RULE BẢO VỆ ADMIN ===
                .requestMatchers("/api/admin/**", "/admin/**", "/manage-account", "/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/comments/admin/**").hasRole("ADMIN")  // Thêm dòng này
                .requestMatchers("/api/content/**", "/manage-movies", "/ContentManagerScreen/**").hasAnyRole("ADMIN", "CONTENT_MANAGER")
                .requestMatchers("/ModeratorScreen/**").hasAnyRole("ADMIN", "MODERATOR")
                // ======================================
                
                // Các rule cũ của bạn
                .requestMatchers("/favorites/api/**").permitAll()
                .requestMatchers("/history", "/api/history/**", "/favorites/**").authenticated()
                .requestMatchers("/ws/**", "/watch-party/**", "/social/**").permitAll() // Cho phép Socket và trang xem chung
                // Lưu ý: Thực tế nên yêu cầu .authenticated() cho "/watch-party/**", nhưng "/ws/**" cần mở để Handshake.

                // --- THÊM DÒNG NÀY ĐỂ API MESSENGER CHẠY ĐƯỢC ---
                .requestMatchers("/api/v1/messenger/**").authenticated()

                // Rule catch-all PHẢI ĐỂ CUỐI CÙNG
                .requestMatchers("/**").permitAll()
            );

        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth2 -> oauth2
                .clientRegistrationRepository(clientRegistrationRepository)
                .loginPage("/login")
                .successHandler(oAuth2LoginSuccessHandler)
                .failureUrl("/login?error=oauth2")
            );
        }

        http
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