package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CategoryPageController {

  @GetMapping("/the-loai")
  public String category(@RequestParam String slug, @RequestParam(required = false) Integer id) {
    return "category";
  }
}
