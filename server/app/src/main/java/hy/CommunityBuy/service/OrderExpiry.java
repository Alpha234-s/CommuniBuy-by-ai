package hy.CommunityBuy.service;

import hy.CommunityBuy.infrastructure.SafeDiagnostics;
import hy.CommunityBuy.repository.OrderRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.orders.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OrderExpiry {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiry.class);
  private final OrderRepository orders;
  private final OrderService service;
  private final Clock clock;

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${app.orders.sweep-delay-ms}")
  public void sweep() {
    // Bounded work per sweep; restart compensation uses the persisted deadline, not process timers.
    try (var ignored = SafeDiagnostics.traceScope()) {
      java.util.List<Long> due;
      try {
        due = orders.expired(clock.instant().getEpochSecond(), PageRequest.of(0, 100));
      } catch (RuntimeException e) {
        log.warn(
            "event=orders.expiry.scan traceId={} diagnostic={}",
            MDC.get("traceId"),
            SafeDiagnostics.describe(e));
        return;
      }
      for (long id : due) {
        try {
          service.expire(id);
        } catch (RuntimeException e) {
          log.warn(
              "event=orders.expiry.deferred orderId={} traceId={} diagnostic={}",
              id,
              MDC.get("traceId"),
              SafeDiagnostics.describe(e));
        }
      }
    }
  }
}
