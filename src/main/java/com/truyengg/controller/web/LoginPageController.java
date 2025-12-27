package com.truyengg.controller.web;

import com.truyengg.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static com.truyengg.domain.enums.UserRole.ADMIN;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

@Controller
public class LoginPageController {

  @GetMapping("/auth/login")
  public String login(@RequestParam(required = false) String redirect,
                      @AuthenticationPrincipal UserPrincipal userPrincipal,
                      Model model) {
    if (userPrincipal != null) {
      if (userPrincipal.role().equals(ADMIN)) {
        return "redirect:/admin/dashboard";
      } else {
        return "redirect:/";
      }
    }

    if (isNotEmpty(redirect)) {
      model.addAttribute("redirectUrl", redirect);
    }

    return "auth/login";
  }

  @GetMapping("/auth/logout")
  public String logout(HttpServletResponse response) {
    var accessTokenCookie = ResponseCookie.from("access_token", "")
        .httpOnly(true)
        .path("/")
        .maxAge(0)
        .build();

    var refreshTokenCookie = ResponseCookie.from("refresh_token", "")
        .httpOnly(true)
        .path("/")
        .maxAge(0)
        .build();

    response.addHeader(SET_COOKIE, accessTokenCookie.toString());
    response.addHeader(SET_COOKIE, refreshTokenCookie.toString());

    return "redirect:/auth/login";
  }
}

