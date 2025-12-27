package com.truyengg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/css/**")
        .addResourceLocations("classpath:/static/css/");
    registry.addResourceHandler("/js/**")
        .addResourceLocations("classpath:/static/js/");
    registry.addResourceHandler("/img/**")
        .addResourceLocations("classpath:/static/img/");
    registry.addResourceHandler("/fonts/**")
        .addResourceLocations("classpath:/static/fonts/");
    registry.addResourceHandler("/voler/**")
        .addResourceLocations("classpath:/static/voler/");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}

