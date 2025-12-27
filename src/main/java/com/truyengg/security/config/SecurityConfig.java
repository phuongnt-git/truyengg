package com.truyengg.security.config;

import com.truyengg.security.jwt.JwtAuthenticationFilter;
import com.truyengg.security.qsc.filter.QSCHPKEFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static java.util.Collections.singletonList;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

  JwtAuthenticationFilter jwtAuthenticationFilter;
  QSCHPKEFilter qscHpkeFilter;
  AuthenticationEntryPoint authenticationEntryPoint;
  AccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/auth/**", "/", "/api/comics/**", "/api/chapters/**",
                "/api/categories/**", "/api/search/**", "/swagger-ui/**", "/v3/api-docs/**",
                "/webjars/**", "/css/**", "/js/**", "/img/**", "/fonts/**",
                "/ws/**", "/ws", "/api/qsc/public-key", "/graphiql",
                "/graphql", "/api/passkey/login/**").permitAll()
            .requestMatchers("/jobrunr/**").hasRole("ADMIN")
            .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/translator/**").hasAnyRole("TRANSLATOR", "ADMIN")
            .anyRequest().authenticated()
        ).
        exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(qscHpkeFilter, JwtAuthenticationFilter.class)
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(singletonList("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(singletonList("*"));
    configuration.setExposedHeaders(singletonList("Authorization"));
    configuration.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }
}

