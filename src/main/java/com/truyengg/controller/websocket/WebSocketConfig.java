package com.truyengg.controller.websocket;

import com.truyengg.security.qsc.HPKEWebSocketInterceptor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  WebSocketAuthenticationInterceptor authenticationInterceptor;
  HPKEWebSocketInterceptor hpkeWebSocketInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // Create TaskScheduler inline for heartbeat (required when heartbeat is configured)
    var taskScheduler = new ThreadPoolTaskScheduler();
    taskScheduler.setPoolSize(1);
    taskScheduler.setThreadNamePrefix("websocket-heartbeat-");
    taskScheduler.initialize();

    // Enable simple broker for /topic destinations with heartbeat
    config.enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[]{1000, 1000})
        .setTaskScheduler(taskScheduler);

    // Set application destination prefix
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Register STOMP endpoint with SockJS fallback
    registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authenticationInterceptor, hpkeWebSocketInterceptor);
  }

}

