package com.truyengg.controller.api.auth;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.TokenResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.security.passkey.PasskeyService;
import com.truyengg.security.passkey.model.PasskeyFinishAuthenticationRequest;
import com.truyengg.security.passkey.model.PasskeyFinishRegistrationRequest;
import com.truyengg.security.passkey.model.PasskeyResponse;
import com.truyengg.security.passkey.model.PasskeyStartAuthenticationRequest;
import com.truyengg.security.passkey.model.PasskeyStartRegistrationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.truyengg.model.response.ApiResponse.error;
import static com.truyengg.model.response.ApiResponse.success;
import static com.truyengg.util.RequestUtils.getClientIpAddress;
import static com.truyengg.util.RequestUtils.getUserAgent;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;


@RestController
@RequestMapping("/api/passkey")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Passkey", description = "WebAuthn/Passkey authentication endpoints")
public class PasskeyController {

  PasskeyService passkeyService;

  @PostMapping("/register/start")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Start passkey registration", description = "Initiates the passkey registration process for the authenticated admin user")
  public ResponseEntity<ApiResponse<String>> startRegistration(
      @AuthenticationPrincipal UserPrincipal user,
      @Valid @RequestBody PasskeyStartRegistrationRequest request) {

    var options = passkeyService.startRegistration(user.id(), request.deviceName());
    return ok(success(options));
  }

  @PostMapping("/register/finish")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Complete passkey registration", description = "Completes the passkey registration process")
  public ResponseEntity<ApiResponse<PasskeyResponse>> finishRegistration(
      @AuthenticationPrincipal UserPrincipal user,
      @Valid @RequestBody PasskeyFinishRegistrationRequest request) {

    var passkey = passkeyService.finishRegistration(user.id(), request);
    return ok(success(passkey));
  }

  @PostMapping("/login/start")
  @Operation(summary = "Start passkey authentication", description = "Initiates the passkey authentication process")
  public ResponseEntity<ApiResponse<String>> startAuthentication(
      @RequestBody(required = false) PasskeyStartAuthenticationRequest request) {

    var email = request != null ? request.email() : null;
    var options = passkeyService.startAuthentication(email);
    return ok(success(options));
  }

  @PostMapping("/login/finish")
  @Operation(summary = "Complete passkey authentication", description = "Completes the passkey authentication and returns JWT tokens")
  public ResponseEntity<ApiResponse<TokenResponse>> finishAuthentication(
      @RequestParam String requestId,
      @Valid @RequestBody PasskeyFinishAuthenticationRequest request,
      HttpServletRequest httpRequest) {

    var remoteAddress = getClientIpAddress(httpRequest);
    var userAgent = getUserAgent(httpRequest);

    var tokenResponse = passkeyService.finishAuthentication(requestId, request, remoteAddress, userAgent);
    return ok(success(tokenResponse));
  }

  @GetMapping("/list")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "List user's passkeys", description = "Returns all registered passkeys for the authenticated user")
  public ResponseEntity<ApiResponse<List<PasskeyResponse>>> listPasskeys(
      @AuthenticationPrincipal UserPrincipal user) {

    var passkeys = passkeyService.getUserPasskeys(user.id());
    return ResponseEntity.ok(success(passkeys));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Delete a passkey", description = "Removes a registered passkey")
  public ResponseEntity<ApiResponse<Void>> deletePasskey(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable UUID id) {
    passkeyService.deletePasskey(user.id(), id);
    return ResponseEntity.ok(success(null));
  }

  @PutMapping("/{id}/name")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Rename a passkey", description = "Updates the device name of a registered passkey")
  public ResponseEntity<ApiResponse<PasskeyResponse>> renamePasskey(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable UUID id,
      @RequestBody Map<String, String> body) {
    var newName = body.get("name");
    if (isBlank(newName)) {
      return badRequest().body(error("Name is required"));
    }

    var passkey = passkeyService.updatePasskeyName(user.id(), id, newName);
    return ok(success(passkey));
  }
}

