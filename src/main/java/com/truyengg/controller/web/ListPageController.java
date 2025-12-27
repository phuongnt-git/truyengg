package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ListPageController {

  @GetMapping("/truyen-moi-cap-nhat")
  public String newComics() {
    return "new-comics";
  }

  @GetMapping("/truyen-moi")
  public String newReleases() {
    return "new-release";
  }

  @GetMapping("/truyen-hoan-thanh")
  public String completedComics() {
    return "completed";
  }

  @GetMapping("/sap-ra-mat")
  public String upcomingComics() {
    return "upcoming";
  }
}
