package com.truyengg.security;

import com.truyengg.domain.entity.User;
import com.truyengg.domain.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.Collection;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonList;

public record UserPrincipal(
    Long id,
    String email,
    String password,
    UserRole role,
    ZonedDateTime bannedUntil,
    Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

  public static UserPrincipal create(User user) {
    return new UserPrincipal(
        user.getId(),
        user.getEmail(),
        user.getPassword(),
        user.getRoles(),
        user.getBannedUntil(),
        singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRoles().name()))
    );
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return bannedUntil == null || bannedUntil.isBefore(now());
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return isAccountNonLocked();
  }
}
