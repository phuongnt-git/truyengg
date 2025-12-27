package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistoryPageController {

  @GetMapping("/lich-su")
  public String historyPage() {
    return "history";
  }
}
