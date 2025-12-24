package com.truyengg.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "truyengg.crawl.job")
@Getter
@Setter
public class CrawlJobLimitProperties {

  private Limit limit = new Limit();
  private Queue queue = new Queue();

  @Getter
  @Setter
  public static class Limit {
    private int perAdmin = 5;
    private int perServer = 25;
  }

  @Getter
  @Setter
  public static class Queue {
    private String cronExpression = "0 */5 * * * *";
  }
}

