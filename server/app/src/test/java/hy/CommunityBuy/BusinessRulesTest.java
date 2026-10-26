package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;

import hy.CommunityBuy.commerce.Amounts;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.service.AuthService;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {
  @Test
  void totalsAndQuantityRejectOverflowInsteadOfWrapping() {
    assertThat(Amounts.add(Long.MAX_VALUE - 1, 1)).isEqualTo(Long.MAX_VALUE);
    assertThat(Amounts.quantity(Integer.MAX_VALUE - 1, 1)).isEqualTo(Integer.MAX_VALUE);
    assertThatThrownBy(() -> Amounts.add(Long.MAX_VALUE, 1))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    assertThatThrownBy(() -> Amounts.quantity(Integer.MAX_VALUE, 1))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
  }

  @Test
  void bcryptLimitUsesUtf8BytesAndAcceptsExactBoundary() {
    AuthService.checkPassword("a".repeat(72));
    AuthService.checkPassword("中".repeat(24));
    assertThatThrownBy(() -> AuthService.checkPassword("a".repeat(73)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> AuthService.checkPassword("中".repeat(25)))
        .isInstanceOf(BusinessException.class);
  }
}
