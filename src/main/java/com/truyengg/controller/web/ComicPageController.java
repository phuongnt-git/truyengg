package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ComicPageController {

  @GetMapping("/truyen-tranh")
  public String comicDetail(@RequestParam String slug) {
    return "comic-detail";
  }
}
