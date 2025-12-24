package com.truyengg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.ZoneId;
import java.util.Optional;

import static java.time.ZonedDateTime.now;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")
public class JpaAuditingConfig {

  @Bean(name = "zonedDateTimeProvider")
  public DateTimeProvider zonedDateTimeProvider() {
    return () -> Optional.of(now(ZoneId.of("Asia/Ho_Chi_Minh")));
  }
}

