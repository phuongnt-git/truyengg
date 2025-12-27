package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountController {

  @GetMapping("/thiet-lap-tai-khoan")
  public String accountSettings() {
    return "account-settings";
  }

  @GetMapping("/nap-xu")
  public String recharge() {
    return "recharge";
  }

  @GetMapping("/lich-su-nap-xu")
  public String rechargeHistory() {
    // Redirect to recharge page with recharge history tab
    return "redirect:/nap-xu?tab=recharge-history";
  }

  @GetMapping("/lich-su-thanh-toan")
  public String paymentHistory() {
    // Redirect to recharge page with payment history tab
    return "redirect:/nap-xu?tab=payment-history";
  }
}
