package com.truyengg.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryCrawlPageController {

  @GetMapping("/category-crawl")
  public String categoryCrawlPage() {
    return "admin/category-crawl";
  }
}

