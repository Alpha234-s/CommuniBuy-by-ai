package hy.CommunityBuy.commerce;

import hy.CommunityBuy.exception.BusinessException;

public final class Amounts {
  private Amounts() {}

  public static long add(long a, long b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw BusinessException.bad("Amount overflow");
    }
  }

  public static int quantity(int a, int b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw BusinessException.bad("Quantity overflow");
    }
  }

  public static String hash(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
