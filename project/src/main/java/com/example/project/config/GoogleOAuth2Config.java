package com.example.project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class GoogleOAuth2Config {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2Config.class);

    @Bean
    @Conditional(GoogleOAuth2Condition.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${google.client.id:${GOOGLE_CLIENT_ID:}}") String clientId,
            @Value("${google.client.secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret) {
        log.info("Google OAuth2 is ENABLED with client ID: {}...", clientId.substring(0, Math.min(12, clientId.length())));
        ClientRegistration googleRegistration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId.trim())
                .clientSecret(clientSecret.trim())
                .scope("openid", "profile", "email")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(googleRegistration);
    }
}
