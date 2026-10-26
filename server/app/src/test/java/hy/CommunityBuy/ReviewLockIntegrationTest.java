package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;

import hy.CommunityBuy.commerce.CommerceDtos.*;
import hy.CommunityBuy.commerce.ShopService;
import hy.CommunityBuy.finance.FinanceDtos.ReviewRequest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@Import(ReviewLockIntegrationTest.LockConfig.class)
class ReviewLockIntegrationTest extends IntegrationSupport {
  @Autowired ShopService shop;
  @Autowired LockGate gate;

  @TestConfiguration
  static class LockConfig {
    @Bean
    LockGate lockGate() {
      return new LockGate();
    }
  }

  // Test-only interception of real repository calls: all locks, FK checks and commits remain MySQL.
  @Aspect
  static class LockGate {
    final ThreadLocal<String> role = new ThreadLocal<>();
    final AtomicReference<Schedule> current = new AtomicReference<>();

    @Around("execution(* hy.CommunityBuy.repository.UserRepository.lock(..))")
    Object user(ProceedingJoinPoint call) throws Throwable {
      var s = current.get();
      if (s != null && "review".equals(role.get())) s.reviewReady.countDown();
      var result = call.proceed();
      if (s != null && "purchase".equals(role.get())) {
        s.userHeld.countDown();
        await(s.reviewReady);
      }
      return result;
    }

    @Around("execution(* hy.CommunityBuy.repository.ItemRepository.lock(..))")
    Object item(ProceedingJoinPoint call) throws Throwable {
      var s = current.get();
      if (s != null && "purchase".equals(role.get())) s.purchaseItemAttempt.countDown();
      return call.proceed();
    }

    @Around("execution(* hy.CommunityBuy.repository.OrderRepository.lock(..))")
    Object order(ProceedingJoinPoint call) throws Throwable {
      var result = call.proceed();
      var s = current.get();
      if (s != null && "review".equals(role.get())) {
        s.reviewReady.countDown();
        await(s.purchaseItemAttempt);
      }
      return result;
    }

    Outcome run(String operation, Supplier<?> work) {
      role.set(operation);
      try {
        return new Outcome(work.get(), null);
      } catch (Throwable error) {
        return new Outcome(null, error);
      } finally {
        role.remove();
      }
    }
  }

  record Schedule(
      CountDownLatch userHeld, CountDownLatch reviewReady, CountDownLatch purchaseItemAttempt) {
    Schedule() {
      this(new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1));
    }
  }

  record Outcome(Object value, Throwable failure) {
    String diagnostic() {
      var text = new StringBuilder();
      for (Throwable t = failure; t != null; t = t.getCause()) {
        text.append(t.getClass().getSimpleName());
        if (t instanceof java.sql.SQLException sql)
          text.append("[vendor=")
              .append(sql.getErrorCode())
              .append(",state=")
              .append(sql.getSQLState())
              .append("]");
        text.append(" ");
      }
      return text.toString();
    }
  }

  static void await(CountDownLatch latch) throws InterruptedException {
    if (!latch.await(10, TimeUnit.SECONDS))
      throw new AssertionError("Controlled lock schedule timed out");
  }

  @ParameterizedTest(name = "review versus purchase, cart={0}")
  @ValueSource(booleans = {false, true})
  void reviewAndPurchaseUseUserBeforeItemIncludingForeignKeys(boolean cart) throws Exception {
    long itemId = item(3).id(), completed = order(itemId, "completed").id();
    orderService.action(buyer, completed, "pay");
    orderService.action(seller, completed, "ship");
    orderService.action(buyer, completed, "receive");
    if (cart) shop.cartChange(buyer, itemId, new CartChange(1, true));
    long version = shop.cart(buyer).version();
    var schedule = new Schedule();
    var pool = Executors.newFixedThreadPool(2);
    gate.current.set(schedule);
    try {
      var purchase =
          pool.submit(
              () ->
                  gate.run(
                      "purchase",
                      () ->
                          cart
                              ? shop.checkout(
                                  buyer, new Checkout("Buyer address", version, 199L), "new-cart")
                              : order(itemId, "new-direct")));
      await(schedule.userHeld);
      var review =
          pool.submit(
              () ->
                  gate.run(
                      "review",
                      () ->
                          orderService.review(
                              buyer, completed, new ReviewRequest(itemId, 5, "Fresh"))));
      var p = purchase.get(20, TimeUnit.SECONDS);
      var r = review.get(20, TimeUnit.SECONDS);
      var softly = new org.assertj.core.api.SoftAssertions();
      softly.assertThat(p.failure()).as("purchase: " + p.diagnostic()).isNull();
      softly.assertThat(r.failure()).as("review: " + r.diagnostic()).isNull();
      softly
          .assertThat(jdbc.queryForObject("select count(*) from orders", Long.class))
          .isEqualTo(2);
      softly
          .assertThat(jdbc.queryForObject("select count(*) from reviews", Long.class))
          .isEqualTo(1);
      softly.assertThat(items.findById(itemId).orElseThrow().getStock()).isEqualTo(1);
      softly.assertThat(orderService.get(buyer, completed).status()).isEqualTo(3);
      // Regardless of a rollback, every committed order has lines and exactly one reservation.
      softly
          .assertThat(jdbc.queryForObject("select count(*) from order_lines", Long.class))
          .isEqualTo(jdbc.queryForObject("select count(*) from orders", Long.class));
      softly
          .assertThat(jdbc.queryForObject("select count(*) from checkout_batches", Long.class))
          .isEqualTo(cart ? 1 : 0);
      softly.assertThat(shop.cart(buyer).lines()).isEmpty();
      softly.assertAll();
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      gate.current.set(null);
    }
  }
}
