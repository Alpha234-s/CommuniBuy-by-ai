package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hy.CommunityBuy.finance.*;
import hy.CommunityBuy.finance.FinanceDtos.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@Import({IntegrationSupport.TimeConfig.class, FinanceIntegrationTest.FaultConfig.class})
class FinanceIntegrationTest extends IntegrationSupport {
  @TestConfiguration
  static class FaultConfig {
    @Bean
    @Primary
    FaultChannel faults(SimulatorChannel real) {
      return new FaultChannel(real);
    }
  }

  static class FaultChannel implements PaymentChannel {
    final SimulatorChannel real;
    final AtomicInteger refundFailures = new AtomicInteger(), chargeFailures = new AtomicInteger();
    final AtomicBoolean pendingRefund = new AtomicBoolean(), badQuery = new AtomicBoolean();

    FaultChannel(SimulatorChannel real) {
      this.real = real;
    }

    public String name() {
      return real.name();
    }

    public Receipt charge(String id, long amount) {
      if (chargeFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
        return new Receipt("FAILED", null, amount, "communibuy-demo");
      return real.charge(id, amount);
    }

    public Receipt refund(String id, long amount) {
      if (refundFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
        return new Receipt("FAILED", null, amount, "communibuy-demo");
      if (pendingRefund.getAndSet(false))
        return new Receipt("PENDING", null, amount, "communibuy-demo");
      return real.refund(id, amount);
    }

    public Receipt query(String kind, String id) {
      if (badQuery.getAndSet(false))
        return new Receipt("SUCCEEDED", "bad-amount", 1L, "communibuy-demo");
      return real.query(kind, id);
    }
  }

  @Autowired FinanceService finance;
  @Autowired FinanceBook book;
  @Autowired FaultChannel faults;
  @Autowired SimulatorChannel channel;

  @BeforeEach
  void clearFaults() {
    faults.refundFailures.set(0);
    faults.chargeFailures.set(0);
    faults.pendingRefund.set(false);
    faults.badQuery.set(false);
  }

  long paid() {
    long id = order(item(3).id(), "paid").id();
    orderService.action(buyer, id, "pay");
    return id;
  }

  long completed() {
    long id = paid();
    orderService.action(seller, id, "ship");
    orderService.action(buyer, id, "receive");
    return id;
  }

  String sign(Notice n) throws Exception {
    var mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            Base64.getDecoder().decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
            "HmacSHA256"));
    return HexFormat.of()
        .formatHex(mac.doFinal(n.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  void callback(String kind, Notice n, String signature, int status) throws Exception {
    mvc.perform(
            post("/api/v1/" + kind + "/simulator/notify")
                .header("X-Channel-Signature", signature)
                .contentType("application/json")
                .content(json.writeValueAsBytes(n)))
        .andExpect(status().is(status));
  }

  @Test
  void independentPaymentRecordUsesTrustedAmountAndIsIdempotent() {
    long id = order(item(3).id(), "payment").id();
    var p = finance.start(buyer, id);
    assertThat(p.status()).isEqualTo("PENDING");
    assertThat(p.amount()).isEqualTo(199);
    assertThat(p.channel()).isEqualTo("SIMULATOR");
    assertThat(finance.start(buyer, id).id()).isEqualTo(p.id());
    assertThat(finance.simulate(buyer, p.id()).status()).isEqualTo("SUCCEEDED");
    finance.simulate(buyer, p.id());
    assertThat(jdbc.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from simulator_operations", Long.class))
        .isEqualTo(1);
    assertThatThrownBy(() -> finance.get(outsider, p.id())).hasMessageContaining("denied");
  }

  @Test
  void paymentCallbackVerifiesSignatureMerchantAmountAndEventIdentity() throws Exception {
    long id = order(item(3).id(), "callback").id();
    var p = finance.start(buyer, id);
    var n = new Notice(p.id(), "event1", p.amount(), p.merchant(), "SUCCEEDED");
    callback("payments", n, "0".repeat(64), 401);
    var amount = new Notice(p.id(), "amount", 1L, p.merchant(), "SUCCEEDED");
    callback("payments", amount, sign(amount), 409);
    var merchant = new Notice(p.id(), "merchant", p.amount(), "other", "SUCCEEDED");
    callback("payments", merchant, sign(merchant), 409);
    callback("payments", n, sign(n), 200);
    callback("payments", n, sign(n), 200);
    assertThat(orderService.get(buyer, id).status()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from channel_events", Long.class)).isEqualTo(1);
    var conflict = new Notice(p.id(), "event1", p.amount(), p.merchant(), "FAILED");
    callback("payments", conflict, sign(conflict), 409);
    assertThat(finance.get(buyer, p.id()).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void activeProviderQueryRecoversMissedCallbackAndRejectsWrongAmount() {
    long id = order(item(3).id(), "query").id();
    var p = finance.start(buyer, id);
    faults.badQuery.set(true);
    assertThatThrownBy(() -> finance.query(buyer, p.id())).hasMessageContaining("amount");
    assertThat(orderService.get(buyer, id).status()).isZero();
    channel.charge(p.id(), p.amount());
    assertThat(finance.get(buyer, p.id()).status()).isEqualTo("PENDING");
    assertThat(finance.query(buyer, p.id()).status()).isEqualTo("SUCCEEDED");
    assertThat(orderService.get(buyer, id).status()).isEqualTo(1);
  }

  @Test
  void cancellationAndPaymentRaceHaveConsistentPaymentAndInventoryRecords() throws Exception {
    long item = item(1).id(), id = order(item, "payment-race").id();
    var p = finance.start(buyer, id);
    List<Boolean> result =
        race(List.of(() -> tryAction(buyer, id, "pay"), () -> tryAction(buyer, id, "cancel")));
    assertThat(result).containsExactlyInAnyOrder(true, false);
    var order = orderService.get(buyer, id);
    var payment = finance.get(buyer, p.id());
    assertThat(payment.status()).isEqualTo(order.status() == 1 ? "SUCCEEDED" : "CANCELLED");
    assertThat(items.findById(item).orElseThrow().getStock())
        .isEqualTo(order.status() == 1 ? 0 : 1);
  }

  @Test
  void lateCallbackCannotResurrectCancelledOrder() throws Exception {
    long id = order(item(2).id(), "late").id();
    var p = finance.start(buyer, id);
    orderService.action(buyer, id, "cancel");
    var n = new Notice(p.id(), "late", p.amount(), p.merchant(), "SUCCEEDED");
    callback("payments", n, sign(n), 409);
    assertThat(orderService.get(buyer, id).status()).isEqualTo(-1);
    assertThat(finance.get(buyer, p.id()).status()).isEqualTo("CANCELLED");
  }

  @Test
  void refundApprovalEnforcesRolesAndRestoresOnlyUnshippedInventoryOnce() {
    long id = paid(), item = orders.findById(id).orElseThrow().getItemId();
    var r = finance.request(buyer, id, "Wrong item");
    assertThatThrownBy(() -> finance.review(buyer, r.id(), "approve"))
        .hasMessageContaining("denied");
    assertThatThrownBy(() -> finance.refund(outsider, r.id())).hasMessageContaining("denied");
    assertThat(finance.review(seller, r.id(), "approve").status()).isEqualTo("SUCCEEDED");
    finance.review(seller, r.id(), "approve");
    assertThat(finance.request(buyer, id, "Wrong item").id()).isEqualTo(r.id());
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(3);
    assertThat(orderService.get(buyer, id).refundedTotal()).isEqualTo(199);
    assertThat(jdbc.queryForObject("select count(*) from refunds", Long.class)).isEqualTo(1);
    var stats = admin.stats(administrator);
    assertThat(stats.grossPaidAmount()).isEqualTo(java.math.BigInteger.valueOf(199));
    assertThat(stats.refundAmount()).isEqualTo(java.math.BigInteger.valueOf(199));
    assertThat(stats.netPaidAmount()).isZero();
  }

  @Test
  void failedRefundIsPersistedAndRetryUsesTheSameProviderOperation() {
    long id = paid(), item = orders.findById(id).orElseThrow().getItemId();
    var r = finance.request(buyer, id, "Refund");
    faults.refundFailures.set(1);
    var failed = finance.review(seller, r.id(), "approve");
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.attempts()).isEqualTo(1);
    assertThat(orderService.get(buyer, id).status()).isEqualTo(1);
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(2);
    var retried = finance.review(seller, r.id(), "retry");
    assertThat(retried.status()).isEqualTo("SUCCEEDED");
    assertThat(retried.id()).isEqualTo(r.id());
    assertThat(retried.attempts()).isEqualTo(2);
    finance.review(seller, r.id(), "retry");
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(3);
  }

  @Test
  void signedRefundNotificationsAreIdempotentAndRequirePriorApproval() throws Exception {
    long id = paid(), item = orders.findById(id).orElseThrow().getItemId();
    var r = finance.request(buyer, id, "Refund");
    var p = book.payment(r.paymentId());
    var n = new Notice(r.id(), "refund-event", r.amount(), p.merchant(), "SUCCEEDED");
    callback("refunds", n, sign(n), 409);
    faults.pendingRefund.set(true);
    assertThat(finance.review(seller, r.id(), "approve").status()).isEqualTo("APPROVED");
    callback("refunds", n, "0".repeat(64), 401);
    callback("refunds", n, sign(n), 200);
    callback("refunds", n, sign(n), 200);
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(3);
    assertThat(finance.refund(buyer, r.id()).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void pendingRefundBlocksShippingAndRejectedRefundDoesNotMoveFunds() {
    long id = paid();
    var r = finance.request(buyer, id, "Review");
    assertThatThrownBy(() -> orderService.action(seller, id, "ship"))
        .hasMessageContaining("progress");
    assertThat(finance.review(seller, r.id(), "reject").status()).isEqualTo("REJECTED");
    finance.review(seller, r.id(), "reject");
    orderService.action(seller, id, "ship");
    assertThat(orderService.get(buyer, id).status()).isEqualTo(2);
    assertThat(admin.stats(administrator).refundAmount()).isZero();
  }

  @Test
  void completedBuyerMayReviewEachLineOnceButOtherActorsAndStatesCannot() throws Exception {
    long id = paid(), item = orders.findById(id).orElseThrow().getItemId();
    var request = new ReviewRequest(item, 5, "Fresh fruit");
    assertThatThrownBy(() -> orderService.review(buyer, id, request)).hasMessageContaining("state");
    orderService.action(seller, id, "ship");
    orderService.action(buyer, id, "receive");
    assertThatThrownBy(() -> orderService.review(seller, id, request))
        .hasMessageContaining("denied");
    orderService.review(buyer, id, request);
    assertThatThrownBy(() -> orderService.review(buyer, id, request))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(finance.reviews(item, 0, 20).count()).isEqualTo(1);
    request("buyer", "POST", "/api/v1/order/" + id + "/reviews", new ReviewRequest(item, 6, "bad"))
        .andExpect(status().isBadRequest());
    request(
            "buyer",
            "POST",
            "/api/v1/order/" + id + "/reviews",
            new ReviewRequest(item, 5, "<script>"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void completedOrdersSettleExactlyOnceAndSettlementIsPrivate() {
    long id = completed();
    var first = finance.settle(seller, "seller", "batch");
    assertThat(first.netAmount()).isEqualTo(199);
    assertThat(first.entries()).hasSize(1);
    assertThat(finance.settle(seller, "seller", "batch").id()).isEqualTo(first.id());
    assertThatThrownBy(() -> finance.settle(seller, "seller", "new"))
        .hasMessageContaining("eligible");
    assertThatThrownBy(() -> finance.settlement(outsider, first.id()))
        .hasMessageContaining("denied");
    assertThat(first.entries().getFirst().orderId()).isEqualTo(id);
  }

  @Test
  void refundAfterSettlementCreatesSingleNegativeAdjustmentAndNeverRestocksShippedGoods() {
    long id = completed(), item = orders.findById(id).orElseThrow().getItemId();
    var sale = finance.settle(seller, "seller", "sale");
    var r = finance.request(buyer, id, "After sale");
    finance.review(seller, r.id(), "approve");
    finance.review(seller, r.id(), "approve");
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(2);
    var adjustment = finance.settle(seller, "seller", "adjustment");
    assertThat(adjustment.netAmount()).isEqualTo(-199);
    assertThat(adjustment.entries()).hasSize(1);
    assertThat(adjustment.entries().getFirst().kind()).isEqualTo("REFUND");
    assertThat(sale.netAmount() + adjustment.netAmount()).isZero();
    assertThat(admin.stats(administrator).netPaidAmount()).isZero();
  }

  @Test
  void refundBeforeSettlementMakesOrderIneligible() {
    long id = completed();
    var r = finance.request(buyer, id, "Return");
    finance.review(seller, r.id(), "approve");
    assertThatThrownBy(() -> finance.settle(seller, "seller", "none"))
        .hasMessageContaining("eligible");
    assertThat(jdbc.queryForObject("select count(*) from settlement_entries", Long.class)).isZero();
  }

  @Test
  void simultaneousSettlementBatchesDoNotPayTheSameOrderTwice() throws Exception {
    completed();
    Supplier<Boolean> first = () -> trySettle("first"), second = () -> trySettle("second");
    assertThat(race(List.of(first, second))).containsExactlyInAnyOrder(true, false);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_entries where kind='SALE'", Long.class))
        .isEqualTo(1);
  }

  boolean trySettle(String key) {
    try {
      finance.settle(seller, "seller", key);
      return true;
    } catch (hy.CommunityBuy.exception.BusinessException e) {
      assertThat(e.getStatus()).isEqualTo(409);
      return false;
    }
  }

  @Test
  void refundAndSettlementCompetitionPreservesLedgerNet() throws Exception {
    long id = completed();
    race(
        List.<Supplier<Boolean>>of(
            () -> trySettle("race"),
            () -> {
              var refund = finance.request(buyer, id, "Compete");
              finance.review(seller, refund.id(), "approve");
              return true;
            }));
    trySettle("remaining-adjustment");
    assertThat(
            jdbc.queryForObject(
                "select coalesce(sum(amount),0) from settlement_entries", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_entries where kind='SALE'", Long.class))
        .isLessThanOrEqualTo(1);
    assertThat(orderService.get(buyer, id).refundedTotal()).isEqualTo(199);
  }

  @Test
  void zeroPricedRefundRepeatsKeepStockAndCloseTimeStable() throws Exception {
    var item =
        itemService.create(
            seller, new hy.CommunityBuy.dto.Dtos.NewItem("Free", null, 0, 1, 0, 0L, null));
    long id = order(item.id(), "free").id();
    orderService.action(buyer, id, "pay");
    var r = finance.request(buyer, id, "Free refund");
    finance.review(seller, r.id(), "approve");
    long closed = orderService.get(buyer, id).closeTime();
    clock.now.set(clock.instant().plusSeconds(30));
    var p = book.payment(r.paymentId());
    var n = new Notice(r.id(), "free-refund-event", 0L, p.merchant(), "SUCCEEDED");
    callback("refunds", n, sign(n), 200);
    callback("refunds", n, sign(n), 200);
    assertThat(orderService.get(buyer, id).closeTime()).isEqualTo(closed);
    assertThat(items.findById(item.id()).orElseThrow().getStock()).isEqualTo(1);
  }

  @Test
  void platformAggregatesExceedingLongStayExactAndSettlementSplitsAtCapacity() {
    for (int n = 0; n < 3; n++) {
      var item =
          itemService.create(
              seller,
              new hy.CommunityBuy.dto.Dtos.NewItem(
                  "Large " + n, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0L, null));
      var order =
          orderService.create(
              buyer,
              new hy.CommunityBuy.dto.Dtos.NewOrder(item.id(), Integer.MAX_VALUE, "address"),
              "large-" + n);
      orderService.action(buyer, order.id(), "pay");
      orderService.action(seller, order.id(), "ship");
      orderService.action(buyer, order.id(), "receive");
    }
    var expected =
        java.math.BigInteger.valueOf(Integer.MAX_VALUE)
            .pow(2)
            .multiply(java.math.BigInteger.valueOf(3));
    assertThat(admin.stats(administrator).grossPaidAmount()).isEqualTo(expected);
    assertThat(admin.stats(administrator).totalRevenue()).isEqualTo(expected);
    var first = finance.settle(seller, "seller", "capacity-first");
    var second = finance.settle(seller, "seller", "capacity-next");
    assertThat(
            java.math.BigInteger.valueOf(first.netAmount())
                .add(java.math.BigInteger.valueOf(second.netAmount())))
        .isEqualTo(expected);
    assertThat(jdbc.queryForObject("select count(*) from settlement_entries", Long.class))
        .isEqualTo(3);
  }

  @Test
  void failedChargeKeepsPaymentHistoryAndRetryDoesNotReserveAgain() {
    long item = item(3).id(), id = order(item, "failed-charge").id();
    var p = finance.start(buyer, id);
    faults.chargeFailures.set(1);
    assertThat(finance.simulate(buyer, p.id()).status()).isEqualTo("FAILED");
    assertThat(orderService.get(buyer, id).status()).isZero();
    assertThat(orderService.get(buyer, id).payTime()).isNull();
    assertThat(finance.simulate(buyer, p.id()).status()).isEqualTo("SUCCEEDED");
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);
  }
}
