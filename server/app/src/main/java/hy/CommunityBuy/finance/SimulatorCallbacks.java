package hy.CommunityBuy.finance;

import hy.CommunityBuy.dto.Api;
import hy.CommunityBuy.finance.FinanceDtos.*;
import hy.CommunityBuy.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "simulator")
public class SimulatorCallbacks {
  private final OrderService orders;

  @PostMapping("/payments/simulator/notify")
  public Api<Payment> payment(
      @Valid @RequestBody Notice n, @RequestHeader("X-Channel-Signature") String signature) {
    return Api.ok(orders.paymentNotice(n, signature));
  }

  @PostMapping("/refunds/simulator/notify")
  public Api<Refund> refund(
      @Valid @RequestBody Notice n, @RequestHeader("X-Channel-Signature") String signature) {
    return Api.ok(orders.refundNotice(n, signature));
  }
}
