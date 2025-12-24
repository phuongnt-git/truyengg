package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChapterPageController {

  @GetMapping("/chapter")
  public String chapter(@RequestParam String slug, @RequestParam String chapter) {
    return "chapter";
  }
}
