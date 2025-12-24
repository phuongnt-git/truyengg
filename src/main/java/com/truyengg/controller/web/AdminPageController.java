package com.truyengg.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

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

  @GetMapping("/reports")
  public String reports() {
    return "admin/reports";
  }

  @GetMapping("/crawl")
  public String crawl() {
    return "admin/crawl";
  }

  @GetMapping("/backup")
  public String backup() {
    return "admin/backup";
  }

  @GetMapping("/jobs")
  public String jobs() {
    return "admin/jobs";
  }

  @GetMapping("/crawl/jobs/{jobId}")
  public String crawlJobDetail(@PathVariable UUID jobId) {
    return "admin/crawl-job-detail";
  }

  @GetMapping("/images")
  public String images() {
    return "admin/images";
  }
}
