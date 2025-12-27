package com.truyengg.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Utility class for extracting information from HTTP requests.
 */
@UtilityClass
public class RequestUtils {

  /**
   * Common headers used by proxies and load balancers to pass the original client IP.
   * Order matters - more reliable/standard headers come first.
   */
  private static final List<String> IP_HEADERS = Arrays.asList(
      "X-Forwarded-For",           // De-facto standard, used by most proxies/load balancers
      "X-Real-IP",                 // Nginx proxy
      "CF-Connecting-IP",          // Cloudflare
      "True-Client-IP",            // Akamai, Cloudflare Enterprise
      "X-Client-IP",               // Apache mod_proxy
      "Forwarded",                 // RFC 7239 standard header
      "X-Cluster-Client-IP",       // Rackspace, Riverbed
      "Fastly-Client-IP",          // Fastly CDN
      "X-Forwarded",               // General forward header
      "Forwarded-For",             // Variation
      "X-Original-Forwarded-For",  // Some proxies use this
      "Proxy-Client-IP",           // WebLogic
      "WL-Proxy-Client-IP",        // WebLogic
      "HTTP_X_FORWARDED_FOR",      // PHP-style header
      "HTTP_X_FORWARDED",          // PHP-style header
      "HTTP_X_CLUSTER_CLIENT_IP",  // PHP-style header
      "HTTP_CLIENT_IP",            // PHP-style header
      "HTTP_FORWARDED_FOR",        // PHP-style header
      "HTTP_FORWARDED",            // PHP-style header
      "HTTP_VIA",                  // PHP-style header
      "REMOTE_ADDR"                // Fallback
  );

  private static final String LOCALHOST_IPV4 = "127.0.0.1";
  private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
  private static final String UNKNOWN = "unknown";

  /**
   * Extracts the real client IP address from an HTTP request.
   * Handles various proxy headers and load balancer configurations.
   *
   * @param request the HTTP servlet request
   * @return the client IP address, or "unknown" if it cannot be determined
   */
  public static String getClientIpAddress(HttpServletRequest request) {
    if (request == null) {
      return UNKNOWN;
    }

    String ip = null;

    // Check all known headers
    for (var header : IP_HEADERS) {
      ip = request.getHeader(header);
      if (isValidIp(ip)) {
        // Handle comma-separated list (X-Forwarded-For can contain multiple IPs)
        ip = extractFirstIp(ip, header);
        if (isValidIp(ip)) {
          break;
        }
      }
    }

    // Fallback to remote address
    if (!isValidIp(ip)) {
      ip = request.getRemoteAddr();
    }

    // Handle localhost IPv6
    if (LOCALHOST_IPV6.equals(ip)) {
      ip = LOCALHOST_IPV4;
    }

    // Try to resolve hostname to IP if needed
    if (isValidIp(ip) && !isIpAddress(ip)) {
      ip = resolveHostname(ip);
    }

    return ip != null ? ip : UNKNOWN;
  }

  /**
   * Extracts the first valid IP from a header value.
   * Handles RFC 7239 Forwarded header format and comma-separated lists.
   *
   * @param headerValue the header value
   * @param headerName  the header name for special handling
   * @return the first valid IP address
   */
  private static String extractFirstIp(String headerValue, String headerName) {
    if (isBlank(headerValue)) {
      return null;
    }

    // Handle RFC 7239 Forwarded header: "for=192.0.2.60;proto=http;by=203.0.113.43"
    if ("Forwarded".equalsIgnoreCase(headerName)) {
      return parseForwardedHeader(headerValue);
    }

    // Handle comma-separated IPs (e.g., "client, proxy1, proxy2")
    // First IP is typically the original client
    if (headerValue.contains(",")) {
      var ips = headerValue.split(",");
      for (var ip : ips) {
        var trimmedIp = ip.trim();
        if (isValidIp(trimmedIp) && isPublicIp(trimmedIp)) {
          return trimmedIp;
        }
      }
      // If no public IP found, return the first one
      return ips[0].trim();
    }

    return headerValue.trim();
  }

  /**
   * Parses RFC 7239 Forwarded header.
   * Format: "for=192.0.2.60;proto=http;by=203.0.113.43"
   * or: "for=\"[2001:db8:cafe::17]:4711\""
   *
   * @param headerValue the Forwarded header value
   * @return the client IP address
   */
  private static String parseForwardedHeader(String headerValue) {
    if (isBlank(headerValue)) {
      return null;
    }

    // Handle multiple forwarded entries
    var entries = headerValue.split(",");
    for (var entry : entries) {
      var parts = entry.trim().split(";");
      for (var part : parts) {
        var keyValue = part.trim().split("=", 2);
        if (keyValue.length == 2 && "for".equalsIgnoreCase(keyValue[0].trim())) {
          var ip = keyValue[1].trim();
          // Remove quotes and brackets for IPv6
          ip = ip.replace("\"", "").replace("[", "").replace("]", "");
          // Remove port if present
          if (ip.contains(":") && !ip.contains("::")) {
            // IPv4 with port
            ip = ip.substring(0, ip.lastIndexOf(':'));
          } else if (ip.contains("]:")) {
            // IPv6 with port
            ip = ip.substring(0, ip.lastIndexOf("]:"));
          }
          if (isValidIp(ip) && isPublicIp(ip)) {
            return ip;
          }
        }
      }
    }
    return null;
  }

  /**
   * Checks if the given string is a valid IP address (not null, not empty, not "unknown").
   *
   * @param ip the IP string to check
   * @return true if valid
   */
  private static boolean isValidIp(String ip) {
    return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
  }

  /**
   * Checks if the given string looks like an IP address (contains dots or colons).
   *
   * @param ip the string to check
   * @return true if it looks like an IP address
   */
  private static boolean isIpAddress(String ip) {
    return ip != null && (ip.contains(".") || ip.contains(":"));
  }

  /**
   * Checks if the IP is a public (routable) address.
   *
   * @param ip the IP address to check
   * @return true if public/routable, false if private, local, or invalid
   */
  private static boolean isPublicIp(String ip) {
    if (isBlank(ip)) {
      return false;
    }

    try {
      var address = InetAddress.getByName(ip);
      return !address.isLoopbackAddress()
          && !address.isSiteLocalAddress()
          && !address.isLinkLocalAddress()
          && !address.isAnyLocalAddress();
    } catch (UnknownHostException e) {
      // If we can't parse it, check common private ranges manually
      return !ip.startsWith("10.")
          && !ip.startsWith("192.168.")
          && !isInPrivateRange172(ip)
          && !ip.equals(LOCALHOST_IPV4)
          && !ip.equals(LOCALHOST_IPV6)
          && !ip.startsWith("127.")
          && !ip.startsWith("169.254.")  // Link-local
          && !ip.startsWith("fc")        // IPv6 unique local
          && !ip.startsWith("fd")        // IPv6 unique local
          && !ip.startsWith("fe80");     // IPv6 link-local
    }
  }

  /**
   * Checks if the IP is in the 172.16.0.0 - 172.31.255.255 private range.
   *
   * @param ip the IP address to check
   * @return true if in 172.16-31.x.x range
   */
  private static boolean isInPrivateRange172(String ip) {
    if (!ip.startsWith("172.")) {
      return false;
    }
    try {
      var secondOctet = parseInt(ip.split("\\.")[1]);
      return secondOctet >= 16 && secondOctet <= 31;
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
      return false;
    }
  }

  /**
   * Resolves a hostname to its IP address.
   *
   * @param hostname the hostname to resolve
   * @return the IP address, or the original hostname if resolution fails
   */
  private static String resolveHostname(String hostname) {
    try {
      return InetAddress.getByName(hostname).getHostAddress();
    } catch (UnknownHostException e) {
      return hostname;
    }
  }

  /**
   * Gets the User-Agent header from the request.
   *
   * @param request the HTTP servlet request
   * @return the User-Agent string, or "unknown" if not present
   */
  public static String getUserAgent(HttpServletRequest request) {
    if (request == null) {
      return UNKNOWN;
    }
    var userAgent = request.getHeader("User-Agent");
    return isBlank(userAgent) ? UNKNOWN : userAgent;
  }

}

