package com.truyengg.security.qsc.filter;

import com.truyengg.security.qsc.HPKEService;
import com.truyengg.security.qsc.QSCException;
import com.truyengg.security.qsc.QSCModeSelector;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class QSCHPKEFilter extends OncePerRequestFilter {

  private final HPKEService hpkeService;
  private final QSCModeSelector modeSelector;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain chain) throws ServletException, IOException {
    if (!modeSelector.shouldEncryptPayload()) {
      chain.doFilter(request, response);
      return;
    }

    var encryptionHeader = request.getHeader("X-QSC-Encrypted");
    if (!"kyber-hpke".equals(encryptionHeader)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "QSC encryption required");
      return;
    }

    try {
      var encryptedBody = request.getInputStream().readAllBytes();
      var decryptedBody = hpkeService.decrypt(encryptedBody);

      var wrappedRequest = new DecryptedHttpServletRequestWrapper(request, decryptedBody);
      var wrappedResponse = new EncryptedHttpServletResponseWrapper(response);

      chain.doFilter(wrappedRequest, wrappedResponse);

      var responseBody = wrappedResponse.getCapturedBody();
      var encryptedResponse = hpkeService.encrypt(responseBody);

      response.setContentType("application/octet-stream");
      response.setHeader("X-QSC-Encrypted", "kyber-hpke");
      response.setHeader("X-QSC-Mode", modeSelector.getMode().name());
      response.getOutputStream().write(encryptedResponse);

    } catch (QSCException e) {
      log.error("[QSC] HPKE filter error", e);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "QSC encryption error");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var path = request.getRequestURI();
    return path.startsWith("/api/qsc/public-key") ||
        path.startsWith("/api/images/proxy") ||
        path.startsWith("/api/images/original-proxy") ||
        path.startsWith("/api/auth/") ||
        path.startsWith("/graphql") ||
        path.startsWith("/graphiql");
  }
}

