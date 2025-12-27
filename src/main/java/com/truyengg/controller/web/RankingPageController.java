package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RankingPageController {

  @GetMapping("/top-ngay")
  public String topDaily() {
    return "top-daily";
  }

  @GetMapping("/top-tuan")
  public String topWeekly() {
    return "top-weekly";
  }

  @GetMapping("/top-thang")
  public String topMonthly() {
    return "top-monthly";
  }
}
