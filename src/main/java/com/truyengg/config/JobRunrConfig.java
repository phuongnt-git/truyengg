package com.truyengg.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
public class JobRunrConfig {

  @Bean
  public StorageProvider storageProvider(DataSource dataSource, JobMapper jobMapper) {
    var storageProvider = new PostgresStorageProvider(dataSource);
    storageProvider.setJobMapper(jobMapper);
    return storageProvider;
  }
}

