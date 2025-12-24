package com.truyengg.security;

import com.truyengg.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider tokenProvider;
  private final CustomUserDetailsService customUserDetailsService;
  private final TokenBlacklistService blacklistService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      var jwt = getJwtFromRequest(request);

      if (hasText(jwt) && tokenProvider.validateToken(jwt) && tokenProvider.isAccessToken(jwt)) {
        if (blacklistService.isTokenBlacklisted(jwt)) {
          filterChain.doFilter(request, response);
          return;
        }

        var userId = tokenProvider.getUserIdFromToken(jwt);
        if (userId > 0) {
          var userDetails = customUserDetailsService.loadUserById(userId);
          var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      }

    } catch (Exception ex) {
      log.error("Could not set user authentication in security context", ex);
    }

    filterChain.doFilter(request, response);
  }

  private String getJwtFromRequest(HttpServletRequest request) {
    var bearerToken = request.getHeader("Authorization");
    if (hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }

    var cookies = request.getCookies();
    if (cookies != null) {
      for (var cookie : cookies) {
        if ("access_token".equals(cookie.getName()) && hasText(cookie.getValue())) {
          var token = cookie.getValue();
          if (token.startsWith("Bearer ")) {
            return token.substring(7);
          }
          return token;
        }
      }
    }

    return EMPTY;
  }
}

