package com.truyengg.security.qsc.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;

public class DecryptedHttpServletRequestWrapper extends HttpServletRequestWrapper {

  private final byte[] body;

  public DecryptedHttpServletRequestWrapper(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  @Override
  public ServletInputStream getInputStream() {
    var byteArrayInputStream = new ByteArrayInputStream(body);

    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return byteArrayInputStream.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
      }

      @Override
      public int read() {
        return byteArrayInputStream.read();
      }
    };
  }
}

