package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FollowPageController {

  @GetMapping("/theo-doi")
  public String followPage() {
    return "follow";
  }
}
