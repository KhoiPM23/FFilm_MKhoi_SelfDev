package com.example.project.config;

import com.example.project.dto.UserSessionDto;
import com.example.project.service.OnlineStatusService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final OnlineStatusService onlineStatusService;

    public WebSocketConfig(OnlineStatusService onlineStatusService) {
        this.onlineStatusService = onlineStatusService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new AuthHandshake())       
                .setHandshakeHandler(new CustomHandshake()) 
                .withSockJS();
    }

    // Lấy User từ HttpSession bỏ vào Attributes
    private class AuthHandshake implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                HttpSession session = servletRequest.getServletRequest().getSession();
                Object user = session.getAttribute("user");
                if (user == null) user = session.getAttribute("moderator");
                if (user == null) user = session.getAttribute("admin");
                
                if (user != null) {
                    UserSessionDto userDto = (UserSessionDto) user;
                    attributes.put("userSession", userDto);
                    onlineStatusService.markOnline(userDto.getId());
                }
            }
            return true;
        }
        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
    }

    //  Dùng User trong Attributes để đặt tên cho kết nối (Principal)
    private class CustomHandshake extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            UserSessionDto user = (UserSessionDto) attributes.get("userSession");
            if (user != null) {
                return new StompPrincipal(user.getUserName()); 
            }
            return null;
        }
    }
    
    // Class định danh
    private class StompPrincipal implements Principal {
        private String name;
        public StompPrincipal(String name) { this.name = name; }
        @Override
        public String getName() { return name; }
    }
}