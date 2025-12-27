package com.truyengg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

/**
 * Configuration for async processing using Java 21 Virtual Threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  /**
   * Virtual thread executor for crawl jobs.
   * Uses Java 21 Virtual Threads for efficient concurrent processing.
   */
  @Bean("virtualThreadExecutor")
  public Executor virtualThreadExecutor() {
    return newVirtualThreadPerTaskExecutor();
  }

  @Override
  public Executor getAsyncExecutor() {
    return virtualThreadExecutor();
  }
}

