package com.truyengg.controller.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Controller for Crawl Manager.
 * Single page application with GraphQL-powered frontend.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class CrawlPageController {

  /**
   * Crawl Manager - Main page.
   * Shows sidebar with all jobs and main panel for details/create.
   */
  @GetMapping("/crawl")
  public String crawlManager(Model model) {
    model.addAttribute("pageTitle", "Crawl Manager");
    return "admin/crawl-manager";
  }

  /**
   * Crawl Manager with job pre-selected.
   * URL-based job selection for direct linking and back/forward support.
   */
  @GetMapping("/crawl/{jobId}")
  public String crawlManagerWithJob(@PathVariable UUID jobId, Model model) {
    model.addAttribute("pageTitle", "Crawl Manager");
    model.addAttribute("jobId", jobId.toString());
    return "admin/crawl-manager";
  }

  /**
   * Crawl History - View all crawl jobs with table/card views.
   */
  @GetMapping("/crawl/history")
  public String crawlHistory(Model model) {
    model.addAttribute("pageTitle", "Crawl History");
    return "admin/crawl-history";
  }

  // ===== Redirects from old URLs to unified Crawl Manager =====

  /**
   * Redirect old /admin/crawl/jobs to unified page.
   */
  @GetMapping("/crawl/jobs")
  public String redirectJobs() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old /admin/crawl/jobs/{id} to unified page with job.
   */
  @GetMapping("/crawl/jobs/{id}")
  public String redirectJobDetail(@PathVariable UUID id) {
    return "redirect:/admin/crawl/" + id;
  }

  /**
   * Redirect old /admin/crawl/queue to unified page.
   */
  @GetMapping("/crawl/queue")
  public String redirectQueue() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old /admin/crawl/jobs/new to unified page.
   */
  @GetMapping("/crawl/jobs/new")
  public String redirectNewJob() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old /admin/category-crawl to unified page.
   */
  @GetMapping("/category-crawl")
  public String redirectCategoryCrawl() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old /admin/jobs to unified page.
   */
  @GetMapping("/jobs")
  public String redirectOldJobs() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old dashboard URL.
   */
  @GetMapping("/crawl-dashboard")
  public String redirectDashboard() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old list URL.
   */
  @GetMapping("/crawl-list")
  public String redirectList() {
    return "redirect:/admin/crawl";
  }

  /**
   * Redirect old detail URL.
   */
  @GetMapping("/crawl-detail/{id}")
  public String redirectDetail(@PathVariable UUID id) {
    return "redirect:/admin/crawl/" + id;
  }
}
