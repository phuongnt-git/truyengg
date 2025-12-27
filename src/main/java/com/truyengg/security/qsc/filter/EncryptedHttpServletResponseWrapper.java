package com.truyengg.security.qsc.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public class EncryptedHttpServletResponseWrapper extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  private final ServletOutputStream servletOutputStream;
  private PrintWriter writer;

  public EncryptedHttpServletResponseWrapper(HttpServletResponse response) {
    super(response);

    this.servletOutputStream = new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
      }

      @Override
      public void write(int b) {
        outputStream.write(b);
      }
    };
  }

  @Override
  public ServletOutputStream getOutputStream() {
    return servletOutputStream;
  }

  @Override
  public PrintWriter getWriter() {
    if (writer == null) {
      writer = new PrintWriter(outputStream);
    }
    return writer;
  }

  public byte[] getCapturedBody() {
    if (writer != null) {
      writer.flush();
    }
    return outputStream.toByteArray();
  }
}

