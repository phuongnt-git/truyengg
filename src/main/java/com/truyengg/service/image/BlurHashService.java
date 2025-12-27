package com.truyengg.service.image;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Service for generating BlurHash strings from images.
 * BlurHash creates a compact 20-30 byte placeholder that can be decoded
 * to a blurred preview instantly while the full image loads.
 * <p>
 * This is a pure Java implementation based on the BlurHash algorithm.
 *
 * @see <a href="https://blurha.sh/">BlurHash</a>
 */
@Service
@Slf4j
public class BlurHashService {

  private static final int SCALED_WIDTH = 32;
  private static final int SCALED_HEIGHT = 32;
  private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

  /**
   * -- GETTER --
   * Check if BlurHash generation is enabled.
   *
   */
  @Getter
  @Value("${truyengg.image.blurhash.enabled:true}")
  private boolean enabled;

  @Value("${truyengg.image.blurhash.component-x:4}")
  private int componentX;

  @Value("${truyengg.image.blurhash.component-y:3}")
  private int componentY;

  /**
   * Encode a BufferedImage to a BlurHash string.
   *
   * @param image the buffered image
   * @return the BlurHash string, or null if encoding fails
   */
  public String encode(BufferedImage image) {
    if (!enabled || image == null) {
      return null;
    }

    try {
      // Scale down image for faster encoding
      var scaled = scaleDown(image, SCALED_WIDTH, SCALED_HEIGHT);

      var width = scaled.getWidth();
      var height = scaled.getHeight();

      // Use configured component values
      var compX = Math.min(Math.max(componentX, 1), 9);
      var compY = Math.min(Math.max(componentY, 1), 9);

      // Extract pixel data
      var pixels = new double[width * height][3];
      for (var y = 0; y < height; y++) {
        for (var x = 0; x < width; x++) {
          var rgb = scaled.getRGB(x, y);
          var idx = y * width + x;
          pixels[idx][0] = sRGBToLinear((rgb >> 16) & 0xFF);
          pixels[idx][1] = sRGBToLinear((rgb >> 8) & 0xFF);
          pixels[idx][2] = sRGBToLinear(rgb & 0xFF);
        }
      }

      // Calculate DCT components
      var factors = new double[compX * compY][3];
      for (var j = 0; j < compY; j++) {
        for (var i = 0; i < compX; i++) {
          var factor = calculateFactor(pixels, width, height, i, j);
          factors[j * compX + i] = factor;
        }
      }

      // Encode to BlurHash string
      return encodeToString(factors, compX, compY);
    } catch (Exception e) {
      log.warn("Error encoding BlurHash from BufferedImage: {}", e.getMessage());
      return EMPTY;
    }
  }

  /**
   * Calculate a single DCT factor.
   */
  private double[] calculateFactor(double[][] pixels, int width, int height, int i, int j) {
    var factor = new double[3];
    var scale = (i == 0 && j == 0) ? 1.0 : 2.0;

    for (var y = 0; y < height; y++) {
      for (var x = 0; x < width; x++) {
        var basis = scale * cos((PI * i * x) / width) * cos((PI * j * y) / height);
        var idx = y * width + x;
        factor[0] += basis * pixels[idx][0];
        factor[1] += basis * pixels[idx][1];
        factor[2] += basis * pixels[idx][2];
      }
    }

    var pixelCount = width * height;
    factor[0] /= pixelCount;
    factor[1] /= pixelCount;
    factor[2] /= pixelCount;

    return factor;
  }

  /**
   * Encode factors to BlurHash string.
   */
  private String encodeToString(double[][] factors, int compX, int compY) {
    var sb = new StringBuilder();

    // Encode size flag (first character)
    var sizeFlag = (compX - 1) + (compY - 1) * 9;
    sb.append(CHARACTERS.charAt(sizeFlag));

    // Calculate and encode quantized maximum AC value
    var maximumValue = 0.0;
    for (var i = 1; i < factors.length; i++) {
      maximumValue = max(maximumValue, abs(factors[i][0]));
      maximumValue = max(maximumValue, abs(factors[i][1]));
      maximumValue = max(maximumValue, abs(factors[i][2]));
    }

    int quantizedMaxValue;
    double realMaxValue;
    if (maximumValue > 0) {
      quantizedMaxValue = (int) max(0, min(82, floor(maximumValue * 166 - 0.5)));
      realMaxValue = (quantizedMaxValue + 1) / 166.0;
    } else {
      quantizedMaxValue = 0;
      realMaxValue = 1;
    }
    sb.append(CHARACTERS.charAt(quantizedMaxValue));

    // Encode DC value (first factor)
    sb.append(encodeDC(factors[0]));

    // Encode AC values (remaining factors)
    for (var i = 1; i < factors.length; i++) {
      sb.append(encodeAC(factors[i], realMaxValue));
    }

    return sb.toString();
  }

  /**
   * Encode DC component.
   */
  private String encodeDC(double[] factor) {
    var intR = linearTosRGB(factor[0]);
    var intG = linearTosRGB(factor[1]);
    var intB = linearTosRGB(factor[2]);
    var value = (intR << 16) + (intG << 8) + intB;
    return encodeBase83(value, 4);
  }

  /**
   * Encode AC component.
   */
  private String encodeAC(double[] factor, double maximumValue) {
    var quantR = (int) max(0, min(18, floor(signPow(factor[0] / maximumValue, 0.5) * 9 + 9.5)));
    var quantG = (int) max(0, min(18, floor(signPow(factor[1] / maximumValue, 0.5) * 9 + 9.5)));
    var quantB = (int) max(0, min(18, floor(signPow(factor[2] / maximumValue, 0.5) * 9 + 9.5)));
    var value = quantR * 19 * 19 + quantG * 19 + quantB;
    return encodeBase83(value, 2);
  }

  /**
   * Encode a value to base83 string.
   */
  private String encodeBase83(int value, int length) {
    var sb = new StringBuilder(length);
    for (var i = 1; i <= length; i++) {
      var digit = (value / (int) pow(83, length - i)) % 83;
      sb.append(CHARACTERS.charAt(digit));
    }
    return sb.toString();
  }

  /**
   * Convert sRGB to linear color space.
   */
  private double sRGBToLinear(int value) {
    var v = value / 255.0;
    return v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4);
  }

  /**
   * Convert linear to sRGB color space.
   */
  private int linearTosRGB(double value) {
    var v = max(0, min(1, value));
    var srgb = v <= 0.0031308 ? v * 12.92 : 1.055 * pow(v, 1 / 2.4) - 0.055;
    return (int) round(max(0, min(255, srgb * 255)));
  }

  /**
   * Sign-preserving power function.
   */
  private double signPow(double value, double exp) {
    return Math.signum(value) * pow(abs(value), exp);
  }

  /**
   * Min helper.
   */
  private double min(double a, double b) {
    return Math.min(a, b);
  }

  /**
   * Scale down an image for faster BlurHash encoding.
   */
  private BufferedImage scaleDown(BufferedImage image, int maxWidth, int maxHeight) {
    var originalWidth = image.getWidth();
    var originalHeight = image.getHeight();

    // Calculate scale factor maintaining aspect ratio
    var scaleX = (double) maxWidth / originalWidth;
    var scaleY = (double) maxHeight / originalHeight;
    var scale = Math.min(scaleX, scaleY);

    if (scale >= 1.0) {
      // Image is already smaller than target, just convert color space
      return convertToRgb(image);
    }

    var newWidth = (int) (originalWidth * scale);
    var newHeight = (int) (originalHeight * scale);

    var scaledImage = new BufferedImage(newWidth, newHeight, TYPE_INT_RGB);
    var graphics = scaledImage.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
    graphics.dispose();

    return scaledImage;
  }

  /**
   * Convert image to RGB color space.
   */
  private BufferedImage convertToRgb(BufferedImage image) {
    if (image.getType() == TYPE_INT_RGB) {
      return image;
    }

    var rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), TYPE_INT_RGB);
    Graphics2D graphics = rgbImage.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();

    return rgbImage;
  }

}
