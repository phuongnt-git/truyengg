package com.truyengg.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@Slf4j
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
                       AuthenticationException authException) throws IOException {
    var requestPath = request.getRequestURI();
    var redirectUrl = request.getRequestURL().toString();

    if (requestPath.startsWith("/api/") || requestPath.startsWith("/graphql")) {
      response.setStatus(SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write("""
          {
            "success": false,
            "message": "Unauthorized"
          }
          """);
      return;
    }

    var loginUrl = "/auth/login";
    if (requestPath.startsWith("/admin")) {
      loginUrl += "?redirect=/admin";
    } else {
      loginUrl += "?redirect=" + encode(redirectUrl, UTF_8);
    }

    response.sendRedirect(loginUrl);
  }
}

