package com.truyengg.security;

import com.truyengg.domain.repository.ComicCrawlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static java.util.UUID.fromString;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

  private final JwtTokenProvider jwtTokenProvider;
  private final ComicCrawlRepository comicCrawlRepository;

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }

    var command = accessor.getCommand();
    if (StompCommand.CONNECT.equals(command)) {
      if (!handleConnect(accessor)) {
        return null;
      }
    } else if (StompCommand.SUBSCRIBE.equals(command) && !isSubscriptionAllowed(accessor)) {
      return null;
    }

    return message;
  }

  private boolean handleConnect(StompHeaderAccessor accessor) {
    var token = extractToken(accessor);
    if (isBlank(token)) {
      return false;
    }

    try {
      if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
        return false;
      }

      var authentication = createAuthentication(token);
      accessor.setUser(authentication);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String extractToken(StompHeaderAccessor accessor) {
    var authHeader = accessor.getFirstNativeHeader("Authorization");
    if (isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    var tokenHeader = accessor.getFirstNativeHeader("token");
    if (isNotBlank(tokenHeader)) {
      return tokenHeader;
    }

    return EMPTY;
  }

  private Authentication createAuthentication(String token) {
    var userId = jwtTokenProvider.getUserIdFromToken(token);
    if (userId == 0) {
      return new AnonymousAuthenticationToken(
          "anonymous", "anonymous", singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }
    var role = defaultIfEmpty(jwtTokenProvider.getRoleFromToken(token), "ANONYMOUS");
    var authorities = singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    return new UsernamePasswordAuthenticationToken(userId, null, authorities);
  }

  private boolean isSubscriptionAllowed(StompHeaderAccessor accessor) {
    var destination = accessor.getDestination();
    if (isBlank(destination) || !destination.startsWith("/topic/crawl-progress/")) {
      return true;
    }

    var principal = accessor.getUser();
    if (principal == null) {
      return false;
    }

    if (!(principal instanceof Authentication authentication)) {
      return false;
    }

    var hasAdminRole = authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    if (hasAdminRole) {
      return true;
    }

    try {
      var jobIdStr = destination.substring("/topic/crawl-progress/".length());
      if (isBlank(jobIdStr)) {
        return false;
      }

      var jobId = fromString(jobIdStr.trim());
      var userId = parseLong(authentication.getName());

      var jobOpt = comicCrawlRepository.findById(jobId);
      if (jobOpt.isEmpty()) {
        return false;
      }

      var job = jobOpt.get();
      var jobOwnerId = job.getCreatedBy().getId();
      return userId == jobOwnerId;
    } catch (Exception e) {
      log.error("Error checking crawl ownership for subscription to: {}", destination, e);
      return false;
    }
  }
}
