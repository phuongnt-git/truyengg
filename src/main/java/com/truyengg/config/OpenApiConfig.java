package com.truyengg.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI truyenGgOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("TruyenGG API")
            .description("API documentation for TruyenGG - Comic Reading Platform")
            .version("1.0.0")
            .contact(new Contact()
                .name("TruyenGG Team")
                .email("support@truyengg.com"))
            .license(new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT")))
        .servers(List.of(
            new Server()
                .url("http://localhost:8080")
                .description("Local Development Server"),
            new Server()
                .url("https://api.truyengg.com")
                .description("Production Server")
        ));
  }
}

