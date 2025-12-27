package com.truyengg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  @Value("${truyengg.app.base-url:http://localhost:8080}")
  private String baseUrl;

  public void sendPasswordResetEmail(String to, String resetToken) {
    try {
      var message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(to);
      message.setSubject("Đặt lại mật khẩu - TruyenGG");
      message.setText(String.format("""
          Xin chào,
          
          Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản TruyenGG.
          
          Vui lòng click vào link sau để đặt lại mật khẩu:
          %s/auth/reset-password?token=%s
          
          Link này sẽ hết hạn sau 24 giờ.
          
          Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.
          
          Trân trọng,
          Đội ngũ TruyenGG
          """, baseUrl, resetToken));

      mailSender.send(message);
      log.info("Password reset email sent to {}", to);
    } catch (Exception e) {
      log.error("Failed to send password reset email to {}", to, e);
      throw new RuntimeException("Không thể gửi email đặt lại mật khẩu", e);
    }
  }

  public void sendWelcomeEmail(String to, String username) {
    try {
      var message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(to);
      message.setSubject("Chào mừng đến với TruyenGG");
      message.setText(String.format("""
          Xin chào %s,
          
          Chào mừng bạn đến với TruyenGG - Nền tảng đọc truyện trực tuyến!
          
          Cảm ơn bạn đã đăng ký tài khoản. Chúc bạn có những trải nghiệm tuyệt vời khi đọc truyện.
          
          Trân trọng,
          Đội ngũ TruyenGG
          """, username));

      mailSender.send(message);
      log.info("Welcome email sent to {}", to);
    } catch (Exception e) {
      log.error("Failed to send welcome email to {}", to, e);
      // Don't throw exception for welcome email failures
    }
  }
}
