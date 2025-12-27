package com.truyengg.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@Slf4j
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
                     AccessDeniedException accessDeniedException) throws IOException {
    var requestPath = request.getRequestURI();

    if (requestPath.startsWith("/api/") || requestPath.startsWith("/graphql")) {
      response.setStatus(SC_FORBIDDEN);
      response.setContentType("application/json");
      response.getWriter().write("""
          {
            "success": false,
            "message": "Forbidden"
          }
          """);
      return;
    }

    var loginUrl = "/auth/login";
    if (requestPath.startsWith("/admin")) {
      loginUrl += "?redirect=/admin";
    } else {
      loginUrl += "?redirect=" + encode(request.getRequestURL().toString(), UTF_8);
    }

    response.sendRedirect(loginUrl);
  }
}

