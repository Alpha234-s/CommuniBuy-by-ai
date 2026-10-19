package hy.CommunityBuy.finance;

import hy.CommunityBuy.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class ChannelConfig {
  @Bean
  @ConditionalOnMissingBean(PaymentChannel.class)
  PaymentChannel disabled() {
    return new PaymentChannel() {
      public String name() {
        return "DISABLED";
      }

      public Receipt charge(String id, long amount) {
        throw BusinessException.conflict("Payment channel is not configured");
      }

      public Receipt refund(String id, long amount) {
        throw BusinessException.conflict("Payment channel is not configured");
      }

      public Receipt query(String kind, String id) {
        throw BusinessException.conflict("Payment channel is not configured");
      }
    };
  }
}
