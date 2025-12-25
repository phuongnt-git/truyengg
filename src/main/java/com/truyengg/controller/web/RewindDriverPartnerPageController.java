package com.truyengg.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RewindDriverPartnerPageController {

  @GetMapping({"/rewind/doi-tac-tai-xe-2025", "/rewind/driver-partner-2025"})
  public String rewindDriverPartner2025(
      @RequestParam(value = "driver_name", required = false) String driverName,
      @RequestParam(value = "total_gbrides", required = false) String totalGbRides,
      @RequestParam(value = "total_gfmrides", required = false) String totalGfmRides,
      @RequestParam(value = "total_gerides", required = false) String totalGeRides,
      @RequestParam(value = "total_5starsreviews", required = false) String total5StarsReviews,
      @RequestParam(value = "pct_5starsreviews", required = false) String pct5StarsReviews,
      @RequestParam(value = "badget_top1", required = false) String badgeTop1,
      @RequestParam(value = "average_cr", required = false) String averageCr,
      Model model
  ) {
    model.addAttribute("driverName", driverName != null ? driverName : "Đối tác");
    model.addAttribute("totalGbRides", totalGbRides != null ? totalGbRides : "0");
    model.addAttribute("totalGfmRides", totalGfmRides != null ? totalGfmRides : "0");
    model.addAttribute("totalGeRides", totalGeRides != null ? totalGeRides : "0");
    model.addAttribute("total5StarsReviews", total5StarsReviews != null ? total5StarsReviews : "0");
    model.addAttribute("pct5StarsReviews", pct5StarsReviews != null ? pct5StarsReviews : "0%");
    model.addAttribute("badgeTop1", badgeTop1 != null ? badgeTop1 : "Huy hiệu");
    model.addAttribute("averageCr", averageCr != null ? averageCr : "0%");

    return "rewind/driver-partner-2025";
  }
}

