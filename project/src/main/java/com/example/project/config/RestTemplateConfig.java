package com.example.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration cho RestTemplate với timeout settings
 * Giúp tránh Connection Reset và timeout issues
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // Set timeouts (milliseconds)
        factory.setConnectTimeout(5000);  // 5 seconds connection timeout
        factory.setReadTimeout(10000);     // 10 seconds read timeout
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        return restTemplate;
    }
}  