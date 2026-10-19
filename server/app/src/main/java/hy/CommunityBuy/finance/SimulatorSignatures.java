package hy.CommunityBuy.finance;

import hy.CommunityBuy.exception.BusinessException;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

@Component
public class SimulatorSignatures {
  private final byte[] key;

  public SimulatorSignatures(
      @Value("${app.payment.mode:disabled}") String mode,
      @Value("${app.payment.simulator-secret:}") String secret,
      Environment env) {
    if ("simulator".equals(mode)) {
      if (env.acceptsProfiles(Profiles.of("deploy")))
        throw new IllegalArgumentException("Simulator cannot run in deploy profile");
      key = Base64.getDecoder().decode(secret);
      if (key.length < 32)
        throw new IllegalArgumentException("PAYMENT_SIM_SECRET requires 32 random bytes in Base64");
    } else key = null;
  }

  private String sign(String payload) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public void verify(FinanceDtos.Notice notice, String signature) {
    if (key == null) throw BusinessException.missing();
    if (signature == null
        || !signature.matches("[a-f0-9]{64}")
        || !java.security.MessageDigest.isEqual(
            sign(notice.canonical()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      throw new BusinessException(401, "Invalid channel signature");
  }
}
