// WebSocketConfig.java - SỬA HOÀN TOÀN
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

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setHandshakeHandler(new UserHandshakeHandler())
                .withSockJS();
    }

    private class HttpSessionHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                     WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                HttpSession session = servletRequest.getServletRequest().getSession(false);
                if (session != null) {
                    UserSessionDto userDto = null;
                    if (session.getAttribute("admin") instanceof UserSessionDto) {
                        userDto = (UserSessionDto) session.getAttribute("admin");
                    } else if (session.getAttribute("contentManager") instanceof UserSessionDto) {
                        userDto = (UserSessionDto) session.getAttribute("contentManager");
                    } else if (session.getAttribute("moderator") instanceof UserSessionDto) {
                        userDto = (UserSessionDto) session.getAttribute("moderator");
                    } else if (session.getAttribute("user") instanceof UserSessionDto) {
                        userDto = (UserSessionDto) session.getAttribute("user");
                    }

                    if (userDto != null) {
                        attributes.put("userId", userDto.getId());
                        attributes.put("userName", userDto.getUserName());
                        attributes.put("httpSessionId", session.getId());
                        attributes.put("userDto", userDto);
                        attributes.put("userSession", userDto);
                    }
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                 WebSocketHandler wsHandler, Exception exception) {
            // Do nothing
        }
    }

    private class UserHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, 
                                        Map<String, Object> attributes) {
            Object userId = attributes.get("userId");
            if (userId != null) {
                return new StompPrincipal(userId.toString());
            }
            return null;
        }
    }

    private static class StompPrincipal implements Principal {
        private final String name;

        public StompPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}