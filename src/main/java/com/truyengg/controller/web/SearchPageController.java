package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SearchPageController {

  @GetMapping("/tim-kiem-nang-cao")
  public String advancedSearch() {
    return "advanced-search";
  }
}
