package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RewindRestaurantPartnerPageController {

  @GetMapping({"/rewind/doi-tac-nha-hang-2025", "/rewind/restaurant-partner-2025"})
  public String rewindRestaurantPartner2025(
      @RequestParam(value = "merchant_name", required = false) String merchantName,
      @RequestParam(value = "province", required = false) String province,
      @RequestParam(value = "item_quantity", required = false) String itemQuantity,
      @RequestParam(value = "item_name", required = false) String itemName,
      @RequestParam(value = "campaign_name", required = false) String campaignName,
      @RequestParam(value = "fastest_growth_month", required = false) String fastestGrowthMonth,
      @RequestParam(value = "time", required = false) String peakTime,
      Model model
  ) {
    model.addAttribute("merchantName", merchantName != null ? merchantName : "Cửa hàng của bạn");
    model.addAttribute("province", province != null ? province : "Việt Nam");
    model.addAttribute("itemQuantity", itemQuantity != null ? itemQuantity : "0");
    model.addAttribute("itemName", itemName != null ? itemName : "Sản phẩm bán chạy");
    model.addAttribute("campaignName", campaignName != null ? campaignName : "Chiến dịch nổi bật");
    model.addAttribute("fastestGrowthMonth", fastestGrowthMonth != null ? fastestGrowthMonth : "Tháng 1");
    model.addAttribute("peakTime", peakTime != null ? peakTime : "19:00 - 20:00");

    return "rewind/restaurant-partner-2025";
  }
}

