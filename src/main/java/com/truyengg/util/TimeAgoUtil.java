package com.truyengg.util;

import java.time.Duration;
import java.time.ZonedDateTime;

public class TimeAgoUtil {
  public static String timeAgo(ZonedDateTime dateTime) {
    if (dateTime == null) {
      return "";
    }

    ZonedDateTime now = ZonedDateTime.now(dateTime.getZone());
    Duration duration = Duration.between(dateTime, now);

    long seconds = duration.getSeconds();
    if (seconds < 60) {
      return seconds + " giây trước";
    }

    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + " phút trước";
    }

    long hours = minutes / 60;
    if (hours < 24) {
      return hours + " giờ trước";
    }

    long days = hours / 24;
    if (days < 30) {
      return days + " ngày trước";
    }

    long months = days / 30;
    if (months < 12) {
      return months + " tháng trước";
    }

    long years = months / 12;
    return years + " năm trước";
  }
}

