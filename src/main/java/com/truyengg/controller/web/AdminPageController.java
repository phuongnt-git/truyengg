package com.truyengg.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPageController {

  @GetMapping
  public String adminIndex() {
    return "admin/dashboard";
  }

  @GetMapping("/dashboard")
  public String dashboard() {
    return "admin/dashboard";
  }

  @GetMapping("/users")
  public String users() {
    return "admin/users";
  }

  @GetMapping("/stories")
  public String stories() {
    return "admin/stories";
  }

  @GetMapping("/comics")
  public String comics() {
    return "admin/comics";
  }

  @GetMapping("/duplicates")
  public String duplicates() {
    return "admin/duplicates";
  }

  @GetMapping("/chapters")
  public String chapters() {
    return "admin/chapters";
  }

  @GetMapping("/settings")
  public String settings() {
    return "admin/settings";
  }

  @GetMapping("/setting")
  public String setting() {
    return "admin/setting";
  }

  @GetMapping("/cache")
  public String cache() {
    return "admin/cache";
  }

  @GetMapping("/reports")
  public String reports() {
    return "admin/reports";
  }

  @GetMapping("/backup")
  public String backup() {
    return "admin/backup";
  }

  @GetMapping("/images")
  public String images() {
    return "admin/images";
  }

  @GetMapping("/passkeys")
  public String passkeys() {
    return "admin/passkey-settings";
  }
}
