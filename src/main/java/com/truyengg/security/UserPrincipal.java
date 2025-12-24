package com.truyengg.security;

import com.truyengg.domain.entity.User;
import com.truyengg.domain.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.Collection;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonList;

@Getter
public class UserPrincipal implements UserDetails {

  private final Long id;
  private final String email;
  private final String password;
  private final UserRole role;
  private final ZonedDateTime bannedUntil;
  private final Collection<? extends GrantedAuthority> authorities;

  public UserPrincipal(User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.password = user.getPassword();
    this.role = user.getRoles();
    this.bannedUntil = user.getBannedUntil();
    this.authorities = singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRoles().name()));
  }

  public static UserPrincipal create(User user) {
    return new UserPrincipal(user);
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

