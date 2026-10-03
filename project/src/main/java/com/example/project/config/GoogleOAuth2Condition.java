package com.example.project.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GoogleOAuth2Condition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String clientId = context.getEnvironment().getProperty("google.client.id");
        if (clientId == null || clientId.isBlank()) {
            clientId = context.getEnvironment().getProperty("GOOGLE_CLIENT_ID");
        }
        String secret = context.getEnvironment().getProperty("google.client.secret");
        if (secret == null || secret.isBlank()) {
            secret = context.getEnvironment().getProperty("GOOGLE_CLIENT_SECRET");
        }

        return clientId != null && !clientId.isBlank() && !clientId.startsWith("${")
                && secret != null && !secret.isBlank() && !secret.startsWith("${");
    }
}
